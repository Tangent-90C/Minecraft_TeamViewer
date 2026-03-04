import logging
import time

from .codec import MsgpackMessageCodec
from .protocol import DigestPacket, PatchPacket, RefreshRequestOutboundPacket, ReportRateHintPacket, SnapshotFullPacket
from .state import ServerState


logger = logging.getLogger("teamviewer.broadcaster")


class Broadcaster:
    """
    广播编排层。

    业务职责：
    - 根据客户端能力分流全量/增量消息；
    - 统一执行“清理 -> 仲裁 -> 广播”的周期流程；
    - 为管理端推送实时快照。
    """

    def __init__(self, state: ServerState) -> None:
        self.state = state
        self._codec = MsgpackMessageCodec()
        self._admin_last_states: dict[str, dict] = {}
        self._last_player_report_hints: dict[str, int] = {}
        self._player_sync_scopes = ("players", "entities", "waypoints")
        self._admin_sync_scopes = ("players", "entities", "waypoints", "playerMarks")

    def _encode_message(self, packet) -> bytes:
        return self._codec.encode(packet)

    def _build_full_message(
        self,
        scope_state: dict,
        *,
        channel: str | None = None,
        extra: dict | None = None,
    ) -> SnapshotFullPacket:
        message = {**scope_state}
        if channel:
            message["channel"] = channel
        if isinstance(extra, dict) and extra:
            message.update(extra)
        return SnapshotFullPacket(**message)

    def _build_patch_message(
        self,
        scope_patch: dict,
        *,
        channel: str | None = None,
        extra: dict | None = None,
    ) -> PatchPacket:
        message = {**scope_patch}
        if channel:
            message["channel"] = channel
        if isinstance(extra, dict) and extra:
            message.update(extra)
        return PatchPacket(**message)

    @staticmethod
    def _snapshot_scope_from_state_map(state_map: dict) -> dict:
        if not isinstance(state_map, dict):
            return {}
        return {object_id: node.get("data", {}) for object_id, node in state_map.items() if isinstance(node, dict)}

    def _build_admin_view_state(self, admin_room: str | None = None) -> dict:
        normalized_room = self.state.normalize_room_code(admin_room)
        allowed_sources = self.state.get_active_sources_in_room(normalized_room)
        room_players = self.state.filter_state_map_by_sources(self.state.players, allowed_sources)
        room_entities = self.state.filter_state_map_by_sources(self.state.entities, allowed_sources)
        room_waypoints = self.state.filter_waypoint_state_by_sources_and_room(
            self.state.waypoints,
            allowed_sources,
            normalized_room,
        )
        return {
            "players": self._snapshot_scope_from_state_map(room_players),
            "entities": self._snapshot_scope_from_state_map(room_entities),
            "waypoints": self._snapshot_scope_from_state_map(room_waypoints),
            "playerMarks": dict(self.state.player_marks),
            "tabState": self.state.build_admin_tab_snapshot(normalized_room),
            "roomCode": normalized_room,
            "connections": sorted(allowed_sources),
            "connections_count": len(allowed_sources),
        }

    @staticmethod
    def _wrap_plain_scope(scope_map: dict) -> dict:
        if not isinstance(scope_map, dict):
            return {}
        return {
            object_id: {"data": value}
            for object_id, value in scope_map.items()
        }

    def _compute_scope_patch_for_scopes(self, old_state: dict, new_state: dict, scopes: tuple[str, ...]) -> dict:
        patch = {}
        for scope in scopes:
            patch[scope] = self.state.compute_scope_patch(
                self._wrap_plain_scope(old_state.get(scope, {})),
                self._wrap_plain_scope(new_state.get(scope, {})),
            )
        return patch

    @staticmethod
    def _has_scope_patch_changes(patch: dict, scopes: tuple[str, ...]) -> bool:
        for scope in scopes:
            if patch.get(scope, {}).get("upsert") or patch.get(scope, {}).get("delete"):
                return True
        return False

    def _compute_admin_patch(self, old_state: dict, new_state: dict) -> dict:
        scope_patch = self._compute_scope_patch_for_scopes(old_state, new_state, self._admin_sync_scopes)

        meta_patch = {}
        if old_state.get("tabState") != new_state.get("tabState"):
            meta_patch["tabState"] = new_state.get("tabState")
        if old_state.get("connections") != new_state.get("connections"):
            meta_patch["connections"] = new_state.get("connections", [])
            meta_patch["connections_count"] = new_state.get("connections_count", 0)

        scope_patch["meta"] = meta_patch
        return scope_patch

    @staticmethod
    def _compact_scope_state(node_scope_state: dict, scopes: tuple[str, ...]) -> dict:
        return {
            scope: ServerState.compact_state_map(node_scope_state.get(scope, {}))
            for scope in scopes
        }

    def _has_admin_patch_changes(self, patch: dict) -> bool:
        return self._has_scope_patch_changes(patch, self._admin_sync_scopes) or bool(patch.get("meta"))

    async def send_admin_snapshot_full(self, admin_id: str) -> None:
        ws = self.state.admin_connections.get(admin_id)
        if ws is None:
            return

        admin_room = self.state.get_admin_room(admin_id)
        view_state = self._build_admin_view_state(admin_room)
        message = self._build_full_message(
            view_state,
            channel="admin",
            extra={"server_time": time.time()},
        )

        await ws.send_bytes(self._encode_message(message))
        self._admin_last_states[admin_id] = view_state

    def _build_visible_state_for_player(self, player_id: str) -> dict:
        allowed_sources = self.state.get_allowed_sources_for_player(player_id)
        player_room = self.state.get_player_room(player_id)
        visible_players = self.state.filter_state_map_by_sources(self.state.players, allowed_sources)
        visible_entities = self.state.filter_state_map_by_sources(self.state.entities, allowed_sources)
        visible_waypoints = self.state.filter_waypoint_state_by_sources_and_room(
            self.state.waypoints,
            allowed_sources,
            player_room,
        )
        return {
            "players": visible_players,
            "entities": visible_entities,
            "waypoints": visible_waypoints,
        }

    async def send_snapshot_full_to_player(self, player_id: str) -> None:
        """向指定玩家推送完整快照（重同步场景）。"""
        ws = self.state.connections.get(player_id)
        if ws is None:
            return
        visible = self._build_visible_state_for_player(player_id)
        compact_scopes = self._compact_scope_state(visible, self._player_sync_scopes)
        compact_scopes["playerMarks"] = dict(self.state.player_marks)
        message = self._build_full_message(compact_scopes)
        await ws.send_bytes(self._encode_message(message))

    async def maybe_send_digest(self, player_id: str, visible_state: dict | None = None) -> None:
        """按节流周期发送摘要，帮助客户端做状态一致性检测。"""
        ws = self.state.connections.get(player_id)
        caps = self.state.connection_caps.get(player_id)
        if ws is None or caps is None:
            return

        now = time.time()
        if now - float(caps.get("lastDigestSent", 0.0)) < self.state.DIGEST_INTERVAL_SEC:
            return

        caps["lastDigestSent"] = now
        if visible_state is None:
            visible_state = self._build_visible_state_for_player(player_id)
        message = DigestPacket(
            hashes={
                "players": self.state.state_digest(visible_state["players"]),
                "entities": self.state.state_digest(visible_state["entities"]),
                "waypoints": self.state.state_digest(visible_state["waypoints"]),
            },
        )
        await ws.send_bytes(self._encode_message(message))

    async def broadcast_admin_updates(self, force_full: bool = False) -> None:
        """向管理端广播增量（必要时全量）。"""
        if not self.state.admin_connections:
            self._admin_last_states = {}
            return

        disconnected = []
        for admin_id, ws in list(self.state.admin_connections.items()):
            try:
                current_state = self._build_admin_view_state(self.state.get_admin_room(admin_id))
                previous_state = self._admin_last_states.get(admin_id)

                if force_full or previous_state is None:
                    message = self._build_full_message(
                        current_state,
                        channel="admin",
                        extra={"server_time": time.time()},
                    )
                    await ws.send_bytes(self._encode_message(message))
                else:
                    patch_state = self._compute_admin_patch(previous_state, current_state)
                    if self._has_admin_patch_changes(patch_state):
                        message = self._build_patch_message(
                            patch_state,
                            channel="admin",
                            extra={"server_time": time.time()},
                        )
                        await ws.send_bytes(self._encode_message(message))

                self._admin_last_states[admin_id] = current_state
            except Exception as e:
                logger.warning("Error sending admin update to %s: %s", admin_id, e)
                disconnected.append(admin_id)

        for admin_id in disconnected:
            if admin_id in self.state.admin_connections:
                del self.state.admin_connections[admin_id]
            if admin_id in self.state.admin_connection_rooms:
                del self.state.admin_connection_rooms[admin_id]
            if admin_id in self._admin_last_states:
                del self._admin_last_states[admin_id]

    async def broadcast_updates(self, force_full_to_delta: bool = False) -> None:
        """统一广播入口：清理超时、计算 patch、按能力下发。"""
        await self.request_preexpiry_refreshes()
        self.state.cleanup_timeouts()
        changes = self.state.refresh_resolved_states()

        changed = self.state.has_patch_changes(changes)

        disconnected = []
        for player_id, ws in list(self.state.connections.items()):
            if not self.state.websocket_is_connected(ws):
                logger.debug(
                    f"Skip delta broadcast to disconnected websocket player={player_id} "
                    f"state=({self.state.websocket_state_label(ws)}) changed={changed}"
                )
                disconnected.append(player_id)
                continue

            try:
                requires_scoped = self.state.requires_scoped_delivery(player_id)
                if requires_scoped:
                    visible = self._build_visible_state_for_player(player_id)
                    if force_full_to_delta or changed:
                        compact_scopes = self._compact_scope_state(visible, self._player_sync_scopes)
                        compact_scopes["playerMarks"] = dict(self.state.player_marks)
                        full_msg = self._build_full_message(compact_scopes)
                        await ws.send_bytes(self._encode_message(full_msg))
                    await self.maybe_send_digest(player_id, visible)
                elif force_full_to_delta:
                    compact_scopes = self._compact_scope_state(
                        {
                            "players": self.state.players,
                            "entities": self.state.entities,
                            "waypoints": self.state.waypoints,
                        },
                        self._player_sync_scopes,
                    )
                    compact_scopes["playerMarks"] = dict(self.state.player_marks)
                    full_msg = self._build_full_message(compact_scopes)
                    await ws.send_bytes(self._encode_message(full_msg))
                elif changed:
                    patch_state = {
                        "players": changes["players"],
                        "entities": changes["entities"],
                        "waypoints": changes["waypoints"],
                    }
                    patch_msg = self._build_patch_message(patch_state)
                    await ws.send_bytes(self._encode_message(patch_msg))

                if not requires_scoped:
                    await self.maybe_send_digest(player_id)
            except RuntimeError as e:
                logger.warning(
                    f"RuntimeError sending delta update to player={player_id} "
                    f"state=({self.state.websocket_state_label(ws)}) changed={changed} "
                    f"force_full={force_full_to_delta}: {e}"
                )
                disconnected.append(player_id)
            except Exception as e:
                logger.warning(
                    f"Error sending delta update to player={player_id} "
                    f"state=({self.state.websocket_state_label(ws)}) changed={changed} "
                    f"force_full={force_full_to_delta}: {e}"
                )
                disconnected.append(player_id)

        for player_id in disconnected:
            self.state.remove_connection(player_id)

        await self.broadcast_admin_updates()

    async def request_preexpiry_refreshes(self) -> None:
        """在对象即将超时前，向对应来源客户端请求该范围内的全量确认。"""
        current_time = time.time()
        refresh_targets = self.state.collect_preexpiry_refresh_requests(current_time)
        if not refresh_targets:
            return

        for source_id, payload in refresh_targets.items():
            await self.send_refresh_request_to_source(
                source_id,
                players=payload.get("players", []),
                entities=payload.get("entities", []),
                reason="expiry_soon",
                current_time=current_time,
                bypass_cooldown=False,
            )

    async def send_refresh_request_to_source(
        self,
        source_id: str,
        players: list,
        entities: list,
        reason: str,
        current_time: float | None = None,
        bypass_cooldown: bool = False,
    ) -> None:
        if not isinstance(source_id, str) or not source_id:
            return

        players = [item for item in players if isinstance(item, str) and item]
        entities = [item for item in entities if isinstance(item, str) and item]
        if not players and not entities:
            return

        now = time.time() if current_time is None else current_time
        if not bypass_cooldown and not self.state.can_send_refresh_request(source_id, now):
            return

        ws = self.state.connections.get(source_id)
        if ws is None:
            return
        if not self.state.websocket_is_connected(ws):
            self.state.remove_connection(source_id)
            return

        message = RefreshRequestOutboundPacket(
            reason=reason,
            serverTime=now,
            players=players,
            entities=entities,
        )
        try:
            await ws.send_bytes(self._encode_message(message))
            self.state.mark_refresh_request_sent(source_id, now)
            logger.debug(
                "Sent refresh_req "
                f"source={source_id} players={len(players)} entities={len(entities)} reason={reason}"
            )
        except Exception as e:
            logger.warning(
                f"Error sending refresh_req to source={source_id} "
                f"state=({self.state.websocket_state_label(ws)}): {e}"
            )
            self.state.remove_connection(source_id)

    async def broadcast_report_rate_hints(self, reason: str = "runtime") -> None:
        broadcast_hz = self.state.broadcast_hz
        for player_id, ws in list(self.state.connections.items()):
            if not self.state.websocket_is_connected(ws):
                continue

            caps = self.state.connection_caps.get(player_id, {})
            suggested_ticks = self.state.negotiate_report_interval_ticks(
                player_id,
                caps.get("preferredReportIntervalTicks"),
                caps.get("minReportIntervalTicks"),
                caps.get("maxReportIntervalTicks"),
            )
            previous_ticks = self._last_player_report_hints.get(player_id)
            if previous_ticks == suggested_ticks:
                continue

            self._last_player_report_hints[player_id] = suggested_ticks
            if isinstance(caps, dict):
                caps["negotiatedReportIntervalTicks"] = suggested_ticks

            packet = ReportRateHintPacket(
                reportIntervalTicks=suggested_ticks,
                broadcastHz=broadcast_hz,
                reason=reason,
            )
            try:
                await ws.send_bytes(self._encode_message(packet))
            except Exception as e:
                logger.warning(
                    "Error sending report_rate_hint to player=%s state=(%s): %s",
                    player_id,
                    self.state.websocket_state_label(ws),
                    e,
                )
                self.state.remove_connection(player_id)
