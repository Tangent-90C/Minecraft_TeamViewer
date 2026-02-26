import json
import time

from .state import ServerState


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

    async def send_snapshot_full_to_player(self, player_id: str) -> None:
        """向指定玩家推送完整快照（重同步场景）。"""
        ws = self.state.connections.get(player_id)
        if ws is None:
            return
        message = {
            "type": "snapshot_full",
            "rev": self.state.revision,
            "players": self.state.compact_state_map(self.state.players),
            "entities": self.state.compact_state_map(self.state.entities),
            "waypoints": self.state.compact_state_map(self.state.waypoints),
        }
        await ws.send_text(json.dumps(message, separators=(",", ":")))

    async def maybe_send_digest(self, player_id: str) -> None:
        """按节流周期发送摘要，帮助客户端做状态一致性检测。"""
        ws = self.state.connections.get(player_id)
        caps = self.state.connection_caps.get(player_id)
        if ws is None or caps is None or not caps.get("delta"):
            return

        now = time.time()
        if now - float(caps.get("lastDigestSent", 0.0)) < self.state.DIGEST_INTERVAL_SEC:
            return

        caps["lastDigestSent"] = now
        message = {
            "type": "digest",
            "rev": self.state.revision,
            "hashes": self.state.build_digests(),
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
            "connections": list(self.state.connections.keys()),
            "connections_count": len(self.state.connections),
            "revision": self.state.revision,
        }

        try:
            message = json.dumps(snapshot_data, separators=(",", ":"))
        except Exception as e:
            print(f"Error serializing snapshot data: {e}")
            return

        disconnected = []
        for admin_id, ws in list(self.state.admin_connections.items()):
            try:
                await ws.send_text(message)
            except Exception as e:
                print(f"Error sending snapshot to admin {admin_id}: {e}")
                disconnected.append(admin_id)

        for admin_id in disconnected:
            if admin_id in self.state.admin_connections:
                del self.state.admin_connections[admin_id]

    async def broadcast_legacy_positions(self) -> None:
        """向 legacy 客户端广播全量 positions 消息。"""
        message_data = {
            "type": "positions",
            "players": dict(self.state.players),
            "entities": dict(self.state.entities),
            "waypoints": dict(self.state.waypoints),
        }

        try:
            message = json.dumps(message_data, separators=(",", ":"))
        except Exception as e:
            print(f"Error serializing legacy positions data: {e}")
            return

        disconnected = []
        for player_uuid, ws in list(self.state.connections.items()):
            if self.state.is_delta_client(player_uuid):
                continue
            if not self.state.websocket_is_connected(ws):
                print(
                    f"Skip legacy broadcast to disconnected websocket player={player_uuid} "
                    f"state=({self.state.websocket_state_label(ws)})"
                )
                disconnected.append(player_uuid)
                continue
            try:
                await ws.send_text(message)
            except Exception as e:
                print(
                    f"Error sending legacy message to player={player_uuid} "
                    f"state=({self.state.websocket_state_label(ws)}): {e}"
                )
                disconnected.append(player_uuid)

        for player_uuid in disconnected:
            self.state.remove_connection(player_uuid)

    async def broadcast_updates(self, force_full_to_delta: bool = False) -> None:
        """统一广播入口：清理超时、计算 patch、按能力下发。"""
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
                print(
                    f"Skip delta broadcast to disconnected websocket player={player_id} "
                    f"state=({self.state.websocket_state_label(ws)}) rev={rev} changed={changed}"
                )
                disconnected.append(player_id)
                continue

            try:
                if self.state.is_delta_client(player_id):
                    if force_full_to_delta:
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

                    await self.maybe_send_digest(player_id)
            except RuntimeError as e:
                print(
                    f"RuntimeError sending delta update to player={player_id} "
                    f"state=({self.state.websocket_state_label(ws)}) rev={rev} changed={changed} "
                    f"force_full={force_full_to_delta}: {e}"
                )
                disconnected.append(player_id)
            except Exception as e:
                print(
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
