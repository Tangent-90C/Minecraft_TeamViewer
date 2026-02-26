import json
import logging
import time

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

    def _build_visible_state_for_player(self, player_id: str) -> dict:
        allowed_sources = self.state.get_allowed_sources_for_player(player_id)
        visible_players = self.state.filter_state_map_by_sources(self.state.players, allowed_sources)
        visible_entities = self.state.filter_state_map_by_sources(self.state.entities, allowed_sources)
        visible_waypoints = self.state.filter_state_map_by_sources(self.state.waypoints, allowed_sources)
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
        message = {
            "type": "snapshot_full",
            "rev": self.state.revision,
            "players": self.state.compact_state_map(visible["players"]),
            "entities": self.state.compact_state_map(visible["entities"]),
            "waypoints": self.state.compact_state_map(visible["waypoints"]),
        }
        await ws.send_text(json.dumps(message, separators=(",", ":")))

    async def maybe_send_digest(self, player_id: str, visible_state: dict | None = None) -> None:
        """按节流周期发送摘要，帮助客户端做状态一致性检测。"""
        ws = self.state.connections.get(player_id)
        caps = self.state.connection_caps.get(player_id)
        if ws is None or caps is None or not caps.get("delta"):
            return

        now = time.time()
        if now - float(caps.get("lastDigestSent", 0.0)) < self.state.DIGEST_INTERVAL_SEC:
            return

        caps["lastDigestSent"] = now
        if visible_state is None:
            visible_state = self._build_visible_state_for_player(player_id)
        message = {
            "type": "digest",
            "rev": self.state.revision,
            "hashes": {
                "players": self.state.state_digest(visible_state["players"]),
                "entities": self.state.state_digest(visible_state["entities"]),
                "waypoints": self.state.state_digest(visible_state["waypoints"]),
            },
        }
        await ws.send_text(json.dumps(message, separators=(",", ":")))

    async def broadcast_snapshot(self) -> None:
        """向管理端广播当前服务端总览。"""
        current_time = time.time()
        snapshot_data = {
            "server_time": current_time,
            "players": dict(self.state.players),
            "entities": dict(self.state.entities),
            "waypoints": dict(self.state.waypoints),
            "playerMarks": dict(self.state.player_marks),
            "tabState": self.state.build_admin_tab_snapshot(),
            "connections": list(self.state.connections.keys()),
            "connections_count": len(self.state.connections),
            "revision": self.state.revision,
        }

        try:
            message = json.dumps(snapshot_data, separators=(",", ":"))
        except Exception as e:
            logger.error("Error serializing snapshot data: %s", e)
            return

        disconnected = []
        for admin_id, ws in list(self.state.admin_connections.items()):
            try:
                await ws.send_text(message)
            except Exception as e:
                logger.warning("Error sending snapshot to admin %s: %s", admin_id, e)
                disconnected.append(admin_id)

        for admin_id in disconnected:
            if admin_id in self.state.admin_connections:
                del self.state.admin_connections[admin_id]

    async def broadcast_legacy_positions(self) -> None:
        """向 legacy 客户端广播全量 positions 消息。"""
        disconnected = []
        for player_uuid, ws in list(self.state.connections.items()):
            if self.state.is_delta_client(player_uuid):
                continue
            if not self.state.websocket_is_connected(ws):
                logger.debug(
                    f"Skip legacy broadcast to disconnected websocket player={player_uuid} "
                    f"state=({self.state.websocket_state_label(ws)})"
                )
                disconnected.append(player_uuid)
                continue
            try:
                if self.state.same_server_filter_enabled:
                    visible = self._build_visible_state_for_player(player_uuid)
                    message_data = {
                        "type": "positions",
                        "players": dict(visible["players"]),
                        "entities": dict(visible["entities"]),
                        "waypoints": dict(visible["waypoints"]),
                    }
                    message = json.dumps(message_data, separators=(",", ":"))
                    await ws.send_text(message)
                else:
                    message_data = {
                        "type": "positions",
                        "players": dict(self.state.players),
                        "entities": dict(self.state.entities),
                        "waypoints": dict(self.state.waypoints),
                    }
                    message = json.dumps(message_data, separators=(",", ":"))
                    await ws.send_text(message)
            except Exception as e:
                logger.warning(
                    f"Error sending legacy message to player={player_uuid} "
                    f"state=({self.state.websocket_state_label(ws)}): {e}"
                )
                disconnected.append(player_uuid)

        for player_uuid in disconnected:
            self.state.remove_connection(player_uuid)

    async def broadcast_updates(self, force_full_to_delta: bool = False) -> None:
        """统一广播入口：清理超时、计算 patch、按能力下发。"""
        await self.request_preexpiry_refreshes()
        self.state.cleanup_timeouts()
        changes = self.state.refresh_resolved_states()

        changed = self.state.has_patch_changes(changes)
        if changed:
            rev = self.state.next_revision()
        else:
            rev = self.state.revision

        disconnected = []
        for player_id, ws in list(self.state.connections.items()):
            if not self.state.websocket_is_connected(ws):
                logger.debug(
                    f"Skip delta broadcast to disconnected websocket player={player_id} "
                    f"state=({self.state.websocket_state_label(ws)}) rev={rev} changed={changed}"
                )
                disconnected.append(player_id)
                continue

            try:
                if self.state.is_delta_client(player_id):
                    if self.state.same_server_filter_enabled:
                        visible = self._build_visible_state_for_player(player_id)
                        if force_full_to_delta or changed:
                            full_msg = {
                                "type": "snapshot_full",
                                "rev": rev,
                                "players": self.state.compact_state_map(visible["players"]),
                                "entities": self.state.compact_state_map(visible["entities"]),
                                "waypoints": self.state.compact_state_map(visible["waypoints"]),
                            }
                            await ws.send_text(json.dumps(full_msg, separators=(",", ":")))
                        await self.maybe_send_digest(player_id, visible)
                    elif force_full_to_delta:
                        full_msg = {
                            "type": "snapshot_full",
                            "rev": rev,
                            "players": self.state.compact_state_map(self.state.players),
                            "entities": self.state.compact_state_map(self.state.entities),
                            "waypoints": self.state.compact_state_map(self.state.waypoints),
                        }
                        await ws.send_text(json.dumps(full_msg, separators=(",", ":")))
                    elif changed:
                        patch_msg = {
                            "type": "patch",
                            "rev": rev,
                            "players": changes["players"],
                            "entities": changes["entities"],
                            "waypoints": changes["waypoints"],
                        }
                        await ws.send_text(json.dumps(patch_msg, separators=(",", ":")))

                    if not self.state.same_server_filter_enabled:
                        await self.maybe_send_digest(player_id)
            except RuntimeError as e:
                logger.warning(
                    f"RuntimeError sending delta update to player={player_id} "
                    f"state=({self.state.websocket_state_label(ws)}) rev={rev} changed={changed} "
                    f"force_full={force_full_to_delta}: {e}"
                )
                disconnected.append(player_id)
            except Exception as e:
                logger.warning(
                    f"Error sending delta update to player={player_id} "
                    f"state=({self.state.websocket_state_label(ws)}) rev={rev} changed={changed} "
                    f"force_full={force_full_to_delta}: {e}"
                )
                disconnected.append(player_id)

        for player_id in disconnected:
            self.state.remove_connection(player_id)

        if changed:
            await self.broadcast_legacy_positions()

        await self.broadcast_snapshot()

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

        message = {
            "type": "refresh_req",
            "reason": reason,
            "serverTime": now,
            "rev": self.state.revision,
            "players": players,
            "entities": entities,
        }
        try:
            await ws.send_text(json.dumps(message, separators=(",", ":")))
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
