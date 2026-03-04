import logging
import os
import time
import uuid
import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from server.broadcaster import Broadcaster
from server.codec import MsgpackMessageCodec
from server.models import EntityData, PlayerData, WaypointData
from server.protocol import (
    AdminAckPacket,
    CommandPlayerMarkClearAllPacket,
    CommandPlayerMarkClearPacket,
    CommandPlayerMarkSetPacket,
    CommandSameServerFilterSetPacket,
    CommandTacticalWaypointSetPacket,
    HandshakeAckPacket,
    HandshakeHelpers,
    HandshakePacket,
    PacketDecodeError,
    PacketParsers,
    PingPacket,
    PongPacket,
    ResyncRequestPacket,
)
from server.state import ServerState



NETWORK_PROTOCOL_VERSION = "0.4.0" # 服务器使用的协议版本
SERVER_MIN_COMPATIBLE_PROTOCOL_VERSION = "0.4.0" # 服务器兼容的最低协议版本
SERVER_PROGRAM_VERSION = "teamviewer-server-dev"


def configure_logging() -> None:
    if logging.getLogger().handlers:
        return

    level_name = os.getenv("TEAMVIEWER_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


configure_logging()
logger = logging.getLogger("teamviewer.main")

message_codec = MsgpackMessageCodec()


async def send_packet(websocket: WebSocket, packet) -> None:
    await websocket.send_bytes(message_codec.encode(packet))


async def receive_payload(websocket: WebSocket) -> dict:
    message = await websocket.receive()
    if message.get("type") == "websocket.disconnect":
        raise WebSocketDisconnect(code=message.get("code", 1000))

    payload = message.get("bytes")
    if payload is None:
        payload = message.get("text")
    if payload is None:
        raise PacketDecodeError("invalid_payload", "payload must be bytes")
    return message_codec.decode(payload)


def resolve_handshake_rejection_reason(packet: HandshakePacket) -> str | None:
    client_protocol = HandshakeHelpers.protocol_version(packet)
    client_min_compatible = HandshakeHelpers.minimum_compatible_protocol_version(packet, client_protocol)

    if not HandshakeHelpers.protocol_at_least(client_protocol, SERVER_MIN_COMPATIBLE_PROTOCOL_VERSION):
        return (
            "client_protocol_too_old: "
            f"client={client_protocol}, required>={SERVER_MIN_COMPATIBLE_PROTOCOL_VERSION}"
        )

    if not HandshakeHelpers.protocol_at_least(NETWORK_PROTOCOL_VERSION, client_min_compatible):
        return (
            "server_protocol_too_old: "
            f"server={NETWORK_PROTOCOL_VERSION}, clientRequires>={client_min_compatible}"
        )

    return None


async def reject_handshake(
    websocket: WebSocket,
    reason: str,
    room_code: str,
) -> None:
    await send_packet(
        websocket,
        HandshakeAckPacket(
            ready=False,
            networkProtocolVersion=NETWORK_PROTOCOL_VERSION,
            minimumCompatibleNetworkProtocolVersion=SERVER_MIN_COMPATIBLE_PROTOCOL_VERSION,
            localProgramVersion=SERVER_PROGRAM_VERSION,
            roomCode=room_code,
            deltaEnabled=True,
            error="version_incompatible",
            rejectReason=reason,
            broadcastHz=state.broadcast_hz,
            playerTimeoutSec=state.PLAYER_TIMEOUT,
            entityTimeoutSec=state.ENTITY_TIMEOUT,
        ),
    )
    close_reason = reason if len(reason) <= 120 else reason[:120]
    await websocket.close(code=1008, reason=close_reason)


def normalize_waypoint_color_to_int(color_value, fallback: int = 0xEF4444) -> int:
    if isinstance(color_value, (int, float)):
        value = int(color_value)
        return max(0, min(value, 0xFFFFFF))

    text = str(color_value or "").strip()
    if not text:
        return fallback

    if text.startswith("#"):
        text = text[1:]
    if text.lower().startswith("0x"):
        text = text[2:]

    if len(text) != 6:
        return fallback

    try:
        return int(text, 16)
    except ValueError:
        return fallback

# 进程级单例：承载内存态与广播能力。
state = ServerState()
broadcaster = Broadcaster(state)
broadcast_task: asyncio.Task | None = None


async def run_broadcast_scheduler() -> None:
    previous_hz: float | None = None
    while True:
        tick_start = time.time()
        try:
            current_hz = state.update_broadcast_hz_for_congestion()
            if previous_hz is None or abs(current_hz - previous_hz) > 1e-6:
                await broadcaster.broadcast_report_rate_hints(
                    reason="startup" if previous_hz is None else "congestion"
                )
                previous_hz = current_hz

            await broadcaster.broadcast_updates()
        except asyncio.CancelledError:
            raise
        except Exception as e:
            logger.exception("Broadcast scheduler error: %s", e)

        interval_sec = 1.0 / max(state.MIN_BROADCAST_HZ, state.broadcast_hz)
        elapsed = time.time() - tick_start
        await asyncio.sleep(max(0.0, interval_sec - elapsed))

# HTTP/WS 入口层：仅做协议收发与调度，不承载核心仲裁逻辑。
@asynccontextmanager
async def lifespan(_app: FastAPI):
    global broadcast_task
    if broadcast_task is None or broadcast_task.done():
        broadcast_task = asyncio.create_task(run_broadcast_scheduler())
    try:
        yield
    finally:
        if broadcast_task is not None:
            broadcast_task.cancel()
            try:
                await broadcast_task
            except asyncio.CancelledError:
                pass
            broadcast_task = None


app = FastAPI(lifespan=lifespan)

@app.websocket("/adminws")
async def admin_ws(websocket: WebSocket):
    """管理端订阅通道：用于查看服务端实时快照。"""
    await websocket.accept()
    admin_id = str(id(websocket))
    state.admin_connections[admin_id] = websocket
    try:
        while True:
            try:
                payload = await receive_payload(websocket)
            except PacketDecodeError as exc:
                await send_packet(websocket, AdminAckPacket(ok=False, error=exc.code))
                continue

            try:
                packet = PacketParsers.parse_admin(payload)
            except PacketDecodeError:
                msg_type = str(payload.get("type") or "").strip()
                await send_packet(
                    websocket,
                    AdminAckPacket(ok=False, error="unsupported_command", command=msg_type or None),
                )
                continue

            if isinstance(packet, HandshakePacket):
                client_protocol = HandshakeHelpers.protocol_version(packet)
                client_program_version = HandshakeHelpers.program_version(packet)
                admin_room = state.set_admin_room(admin_id, HandshakeHelpers.room_code(packet, state.DEFAULT_ROOM_CODE))
                rejection_reason = resolve_handshake_rejection_reason(packet)
                if rejection_reason:
                    logger.warning(
                        "Admin handshake rejected (clientProtocol=%s, roomCode=%s, reason=%s)",
                        client_protocol,
                        admin_room,
                        rejection_reason,
                    )
                    await reject_handshake(websocket, rejection_reason, admin_room)
                    return

                logger.info(
                    "Admin connected (clientProtocol=%s, clientProgramVersion=%s, roomCode=%s)",
                    client_protocol,
                    client_program_version,
                    admin_room,
                )
                await send_packet(
                    websocket,
                    HandshakeAckPacket(
                        networkProtocolVersion=NETWORK_PROTOCOL_VERSION,
                        minimumCompatibleNetworkProtocolVersion=SERVER_MIN_COMPATIBLE_PROTOCOL_VERSION,
                        localProgramVersion=SERVER_PROGRAM_VERSION,
                        roomCode=admin_room,
                        deltaEnabled=True,
                        broadcastHz=state.broadcast_hz,
                        playerTimeoutSec=state.PLAYER_TIMEOUT,
                        entityTimeoutSec=state.ENTITY_TIMEOUT,
                    ),
                )
                await broadcaster.send_admin_snapshot_full(admin_id)
                continue

            if isinstance(packet, PingPacket):
                await send_packet(websocket, PongPacket(serverTime=time.time()))
                continue

            if isinstance(packet, ResyncRequestPacket):
                await broadcaster.send_admin_snapshot_full(admin_id)
                continue

            if isinstance(packet, CommandPlayerMarkSetPacket):
                target_player_id = packet.playerId
                updated_mark = state.set_player_mark(
                    target_player_id,
                    packet.team,
                    packet.color,
                    packet.label,
                    packet.source,
                )

                if updated_mark is None:
                    await send_packet(websocket, AdminAckPacket(ok=False, error="invalid_player_id"))
                    continue

                await send_packet(
                    websocket,
                    AdminAckPacket(
                        ok=True,
                        action="command_player_mark_set",
                        playerId=str(target_player_id).strip() if isinstance(target_player_id, str) else None,
                        mark=updated_mark,
                    ),
                )
                continue

            if isinstance(packet, CommandPlayerMarkClearPacket):
                target_player_id = packet.playerId
                removed = state.clear_player_mark(target_player_id)
                await send_packet(
                    websocket,
                    AdminAckPacket(
                        ok=bool(removed),
                        action="command_player_mark_clear",
                        playerId=target_player_id,
                        error=None if removed else "mark_not_found",
                    ),
                )
                if removed:
                    await broadcaster.broadcast_admin_updates()
                continue

            if isinstance(packet, CommandPlayerMarkClearAllPacket):
                removed_count = state.clear_all_player_marks()
                await send_packet(
                    websocket,
                    AdminAckPacket(
                        ok=True,
                        action="command_player_mark_clear_all",
                        removedCount=removed_count,
                    ),
                )
                continue

            if isinstance(packet, CommandSameServerFilterSetPacket):
                state.same_server_filter_enabled = bool(packet.enabled)
                await send_packet(
                    websocket,
                    AdminAckPacket(
                        ok=True,
                        action="command_same_server_filter_set",
                        enabled=state.same_server_filter_enabled,
                    ),
                )
                continue

            if isinstance(packet, CommandTacticalWaypointSetPacket):
                room_code = state.normalize_room_code(packet.roomCode or packet.roomId or state.get_admin_room(admin_id))
                waypoint_id_raw = packet.waypointId
                waypoint_id = str(waypoint_id_raw).strip() if isinstance(waypoint_id_raw, str) and waypoint_id_raw.strip() else ""
                if not waypoint_id:
                    waypoint_id = f"admin_tactical:{int(time.time() * 1000)}:{uuid.uuid4().hex[:8]}"

                x = packet.x
                z = packet.z
                label = str(packet.label or "战术标记").strip()
                if not label:
                    label = "战术标记"
                if len(label) > 64:
                    label = label[:64]

                dimension = str(packet.dimension or "minecraft:overworld").strip() or "minecraft:overworld"
                tactical_type = str(packet.tacticalType or "attack").strip() or "attack"
                permanent = bool(packet.permanent)
                ttl_seconds_raw = packet.ttlSeconds
                ttl_seconds = None
                if isinstance(ttl_seconds_raw, (int, float)):
                    ttl_seconds = max(10, min(int(ttl_seconds_raw), 86400))
                if permanent:
                    ttl_seconds = None

                waypoint_payload = {
                    "x": x,
                    "y": 64,
                    "z": z,
                    "dimension": dimension,
                    "name": label,
                    "symbol": "T",
                    "color": normalize_waypoint_color_to_int(packet.color, 0xEF4444),
                    "ownerId": None,
                    "ownerName": "Admin Tactical",
                    "createdAt": int(time.time() * 1000),
                    "ttlSeconds": ttl_seconds,
                    "waypointKind": "admin_tactical",
                    "replaceOldQuick": False,
                    "maxQuickMarks": None,
                    "targetType": "block",
                    "targetEntityId": None,
                    "targetEntityType": None,
                    "targetEntityName": None,
                    "roomCode": room_code,
                    "permanent": permanent,
                    "tacticalType": tactical_type,
                    "sourceType": "admin_tactical",
                }

                try:
                    validated = WaypointData(**waypoint_payload)
                except ValidationError:
                    await send_packet(websocket, AdminAckPacket(ok=False, error="invalid_tactical_waypoint_payload"))
                    continue

                admin_source_id = state.build_admin_tactical_source_id(room_code)
                node = state.build_state_node(admin_source_id, time.time(), validated.model_dump())
                state.upsert_report(state.waypoint_reports, waypoint_id, admin_source_id, node)

                await send_packet(
                    websocket,
                    AdminAckPacket(
                        ok=True,
                        action="command_tactical_waypoint_set",
                        waypointId=waypoint_id,
                        waypoint=validated.model_dump(),
                    ),
                )
                continue

            await send_packet(websocket, AdminAckPacket(ok=False, error="unsupported_command"))
    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.exception("Admin websocket error: %s", e)
    finally:
        if admin_id in state.admin_connections:
            del state.admin_connections[admin_id]
        if admin_id in state.admin_connection_rooms:
            del state.admin_connection_rooms[admin_id]


@app.websocket("/playeresp")
async def websocket_endpoint(websocket: WebSocket):
    """
    玩家数据主通道。

    职责：
    1) 接收客户端上报（全量/增量）；
    2) 做入参校验并写入来源报告池；
    3) 触发统一广播（由 Broadcaster 完成聚合后下发）。
    """
    await websocket.accept()
    submit_player_id = None

    try:
        while True:
            try:
                payload = await receive_payload(websocket)
                packet = PacketParsers.parse_player(payload)
            except PacketDecodeError as e:
                logger.debug("Error decoding player packet: %s", e)
                continue

            packet_submit_id = getattr(packet, "submitPlayerId", None)
            if isinstance(packet_submit_id, str) and packet_submit_id:
                submit_player_id = packet_submit_id

            # 握手：建立能力协商（协议版本、是否支持 delta）。
            if isinstance(packet, HandshakePacket):
                rejection_reason = resolve_handshake_rejection_reason(packet)
                if rejection_reason:
                    logger.warning(
                        "Player handshake rejected (submitPlayerId=%s, reason=%s)",
                        submit_player_id,
                        rejection_reason,
                    )
                    await reject_handshake(
                        websocket,
                        rejection_reason,
                        HandshakeHelpers.room_code(packet, state.DEFAULT_ROOM_CODE),
                    )
                    return

                if submit_player_id:
                    state.connections[submit_player_id] = websocket
                    client_protocol = HandshakeHelpers.protocol_version(packet)
                    client_program_version = HandshakeHelpers.program_version(packet)
                    client_room = state.set_player_room(submit_player_id, HandshakeHelpers.room_code(packet, state.DEFAULT_ROOM_CODE))
                    state.mark_player_capability(
                        submit_player_id,
                        client_protocol,
                        packet.preferredReportIntervalTicks,
                        packet.minReportIntervalTicks,
                        packet.maxReportIntervalTicks,
                    )
                    negotiated_ticks = state.negotiate_report_interval_ticks(
                        submit_player_id,
                        packet.preferredReportIntervalTicks,
                        packet.minReportIntervalTicks,
                        packet.maxReportIntervalTicks,
                    )
                    caps = state.connection_caps.get(submit_player_id)
                    if isinstance(caps, dict):
                        caps["negotiatedReportIntervalTicks"] = negotiated_ticks

                    logger.info(
                        "Client %s connected (protocol=%s, programVersion=%s, roomCode=%s)",
                        submit_player_id,
                        client_protocol,
                        client_program_version,
                        client_room,
                    )
                    ack = {
                        "networkProtocolVersion": NETWORK_PROTOCOL_VERSION,
                        "minimumCompatibleNetworkProtocolVersion": SERVER_MIN_COMPATIBLE_PROTOCOL_VERSION,
                        "localProgramVersion": SERVER_PROGRAM_VERSION,
                        "roomCode": client_room,
                        "deltaEnabled": True,
                        "digestIntervalSec": state.DIGEST_INTERVAL_SEC,
                        "broadcastHz": state.broadcast_hz,
                        "reportIntervalTicks": negotiated_ticks,
                        "playerTimeoutSec": state.PLAYER_TIMEOUT,
                        "entityTimeoutSec": state.ENTITY_TIMEOUT,
                    }
                    await send_packet(websocket, HandshakeAckPacket(**ack))
                    await broadcaster.send_snapshot_full_to_player(submit_player_id)
                continue

            if not submit_player_id or submit_player_id not in state.connections:
                logger.debug("Ignore player packet before handshake registration submitPlayerId=%s", submit_player_id)
                continue

            if packet.type == "state_keepalive":
                current_time = time.time()
                touched_players = state.touch_reports(
                    state.player_reports,
                    packet.players,
                    submit_player_id,
                    current_time,
                )
                touched_entities = state.touch_reports(
                    state.entity_reports,
                    packet.entities,
                    submit_player_id,
                    current_time,
                )
                if touched_players or touched_entities:
                    logger.debug(
                        "Applied state_keepalive "
                        f"submitPlayerId={submit_player_id} players={touched_players}/{len(packet.players)} "
                        f"entities={touched_entities}/{len(packet.entities)}"
                    )
                continue

            if packet.type == "players_update":
                # 玩家全量：语义为“该来源本轮玩家状态完整快照”。
                current_time = time.time()
                for pid, player_data in packet.players.items():
                    try:
                        normalized = player_data.model_dump()
                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.player_reports, pid, submit_player_id, node)
                    except Exception as e:
                        logger.warning("Error validating player data for %s: %s", pid, e)

                continue

            if packet.type == "tab_players_update":
                if isinstance(submit_player_id, str) and submit_player_id:
                    current_time = time.time()
                    tab_players = packet.tabPlayers
                    state.upsert_tab_player_report(submit_player_id, tab_players, current_time)
                    await broadcaster.broadcast_admin_updates()
                continue

            if packet.type == "players_patch":
                # 玩家增量：基于该来源已有快照做 merge 后再校验。
                current_time = time.time()
                upsert = packet.upsert
                delete = packet.delete
                missing_baseline_players = []

                for pid, player_data in upsert.items():
                    source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                    existing_node = state.player_reports.get(pid, {}).get(source_key)
                    try:
                        normalized = state.merge_patch_and_validate(PlayerData, existing_node, player_data)
                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.player_reports, pid, submit_player_id, node)
                    except ValidationError as e:
                        missing_fields = state.missing_fields_from_validation_error(e)
                        existing_data = existing_node.get("data") if isinstance(existing_node, dict) else None
                        existing_keys = sorted(existing_data.keys()) if isinstance(existing_data, dict) else []
                        if not isinstance(existing_data, dict):
                            missing_baseline_players.append(pid)
                        logger.warning(
                            "Player patch validation failed "
                            f"pid={pid} submitPlayerId={submit_player_id} sourceKey={source_key!r} "
                            f"hasExistingSnapshot={bool(isinstance(existing_data, dict))} "
                            f"missingFields={missing_fields or '[]'} "
                            f"existingKeys={existing_keys} payload={state.payload_preview(player_data)} "
                            f"errors={state.payload_preview(e.errors(), 480)}"
                        )
                    except Exception as e:
                        logger.exception(
                            "Unexpected error validating player patch "
                            f"pid={pid} submitPlayerId={submit_player_id} payload={state.payload_preview(player_data)}: {e}"
                        )

                if isinstance(delete, list):
                    for pid in delete:
                        if not isinstance(pid, str):
                            continue
                        state.delete_report(state.player_reports, pid, submit_player_id)

                if missing_baseline_players and isinstance(submit_player_id, str) and submit_player_id:
                    await broadcaster.send_refresh_request_to_source(
                        submit_player_id,
                        players=missing_baseline_players,
                        entities=[],
                        reason="missing_baseline_patch",
                        bypass_cooldown=False,
                    )

                continue

            if packet.type == "entities_update":
                # 实体全量：先清理该来源旧实体，再写入本轮实体列表。
                current_time = time.time()
                player_entities = packet.entities
                source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                for entity_id in list(state.entity_reports.keys()):
                    source_bucket = state.entity_reports.get(entity_id, {})
                    if source_key in source_bucket:
                        state.delete_report(state.entity_reports, entity_id, submit_player_id)

                for entity_id, entity_data in player_entities.items():
                    try:
                        normalized = entity_data.model_dump()
                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.entity_reports, entity_id, submit_player_id, node)
                    except Exception as e:
                        logger.warning("Error validating entity data for %s: %s", entity_id, e)

                continue

            if packet.type == "entities_patch":
                # 实体增量：仅修改当前来源 bucket，不影响其他来源。
                current_time = time.time()
                upsert = packet.upsert
                delete = packet.delete
                missing_baseline_entities = []

                for entity_id, entity_data in upsert.items():
                    source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                    existing_node = state.entity_reports.get(entity_id, {}).get(source_key)
                    try:
                        normalized = state.merge_patch_and_validate(EntityData, existing_node, entity_data)
                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.entity_reports, entity_id, submit_player_id, node)
                    except ValidationError as e:
                        missing_fields = state.missing_fields_from_validation_error(e)
                        existing_data = existing_node.get("data") if isinstance(existing_node, dict) else None
                        existing_keys = sorted(existing_data.keys()) if isinstance(existing_data, dict) else []
                        if not isinstance(existing_data, dict):
                            missing_baseline_entities.append(entity_id)
                        logger.warning(
                            "Entity patch validation failed "
                            f"entityId={entity_id} submitPlayerId={submit_player_id} sourceKey={source_key!r} "
                            f"hasExistingSnapshot={bool(isinstance(existing_data, dict))} "
                            f"missingFields={missing_fields or '[]'} "
                            f"existingKeys={existing_keys} payload={state.payload_preview(entity_data)} "
                            f"errors={state.payload_preview(e.errors(), 480)}"
                        )
                    except Exception as e:
                        logger.exception(
                            "Unexpected error validating entity patch "
                            f"entityId={entity_id} submitPlayerId={submit_player_id} "
                            f"payload={state.payload_preview(entity_data)}: {e}"
                        )

                if isinstance(delete, list):
                    for entity_id in delete:
                        if not isinstance(entity_id, str):
                            continue
                        state.delete_report(state.entity_reports, entity_id, submit_player_id)

                if missing_baseline_entities and isinstance(submit_player_id, str) and submit_player_id:
                    await broadcaster.send_refresh_request_to_source(
                        submit_player_id,
                        players=[],
                        entities=missing_baseline_entities,
                        reason="missing_baseline_patch",
                        bypass_cooldown=False,
                    )

                continue

            if packet.type == "waypoints_update":
                # 路标上报：支持 quick 类型数量约束。
                current_time = time.time()
                player_waypoints = packet.waypoints
                for waypoint_id, waypoint_data in player_waypoints.items():
                    try:
                        normalized = waypoint_data.model_dump()

                        if normalized.get("waypointKind") == "quick":
                            max_quick_marks = normalized.get("maxQuickMarks")
                            if isinstance(max_quick_marks, (int, float)):
                                max_quick_marks = max(1, min(int(max_quick_marks), 100))
                            elif bool(normalized.get("replaceOldQuick")):
                                max_quick_marks = 1
                            else:
                                max_quick_marks = None

                            if max_quick_marks is not None:
                                # 限流策略：超出上限时按最旧 quick 路标淘汰。
                                old_quick_waypoints = [
                                    (wid, source_bucket[submit_player_id])
                                    for wid, source_bucket in list(state.waypoint_reports.items())
                                    if wid != waypoint_id
                                    and isinstance(source_bucket, dict)
                                    and submit_player_id in source_bucket
                                    and isinstance(source_bucket[submit_player_id], dict)
                                    and isinstance(source_bucket[submit_player_id].get("data"), dict)
                                    and source_bucket[submit_player_id]["data"].get("waypointKind") == "quick"
                                ]

                                remove_count = len(old_quick_waypoints) - max_quick_marks + 1
                                if remove_count > 0:
                                    old_quick_waypoints.sort(key=lambda item: state.node_timestamp(item[1]))
                                    for old_id, _ in old_quick_waypoints[:remove_count]:
                                        state.delete_report(state.waypoint_reports, old_id, submit_player_id)

                        node = state.build_state_node(submit_player_id, current_time, normalized)
                        state.upsert_report(state.waypoint_reports, waypoint_id, submit_player_id, node)
                    except Exception as e:
                        logger.warning("Error validating waypoint data for %s: %s", waypoint_id, e)

                continue

            if packet.type == "waypoints_delete":
                # 路标删除：仅删除当前来源提交的目标路标。
                waypoint_ids = packet.waypointIds

                for waypoint_id in waypoint_ids:
                    if not isinstance(waypoint_id, str):
                        continue
                    state.delete_report(state.waypoint_reports, waypoint_id, submit_player_id)

                continue

            if packet.type == "waypoints_entity_death_cancel":
                # 实体死亡撤销：清理 targetEntityId 命中的 entity 类型路标。
                target_entity_ids = packet.targetEntityIds

                target_entity_id_set = {
                    entity_id for entity_id in target_entity_ids
                    if isinstance(entity_id, str) and entity_id.strip()
                }

                if target_entity_id_set:
                    for waypoint_id in list(state.waypoint_reports.keys()):
                        source_bucket = state.waypoint_reports.get(waypoint_id)
                        if not isinstance(source_bucket, dict):
                            continue

                        for source_id in list(source_bucket.keys()):
                            node = source_bucket.get(source_id)
                            if not isinstance(node, dict):
                                continue
                            payload = node.get("data")
                            if not isinstance(payload, dict):
                                continue
                            if payload.get("targetType") != "entity":
                                continue
                            if payload.get("targetEntityId") not in target_entity_id_set:
                                continue
                            state.delete_report(state.waypoint_reports, waypoint_id, source_id)

                continue

            if isinstance(packet, ResyncRequestPacket) and submit_player_id:
                # 客户端主动请求全量重同步。
                try:
                    await broadcaster.send_snapshot_full_to_player(submit_player_id)
                except Exception as e:
                    logger.warning("Error sending snapshot_full to %s: %s", submit_player_id, e)
                continue

    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.exception("Error handling player message: %s", e)
    finally:
        if submit_player_id:
            state.remove_connection(submit_player_id)
            logger.info("Client %s disconnected", submit_player_id)


@app.get("/health")
async def health_check():
    """健康检查：用于探活。"""
    return JSONResponse({"status": "ok"})


@app.get("/snapshot")
async def snapshot(roomCode: str | None = None):
    """调试快照：返回当前最终视图与连接状态。"""
    current_time = time.time()

    connections_by_room: dict[str, list[str]] = {}
    for player_id in state.connections.keys():
        if not isinstance(player_id, str) or not player_id:
            continue
        room = state.get_player_room(player_id)
        connections_by_room.setdefault(room, []).append(player_id)

    for room in list(connections_by_room.keys()):
        connections_by_room[room].sort()

    active_rooms = sorted(connections_by_room.keys())
    requested_room = state.normalize_room_code(roomCode) if roomCode is not None else None
    selected_room = requested_room if requested_room is not None else state.DEFAULT_ROOM_CODE
    selected_sources = state.get_active_sources_in_room(selected_room)

    selected_players = state.filter_state_map_by_sources(state.players, selected_sources)
    selected_entities = state.filter_state_map_by_sources(state.entities, selected_sources)
    selected_waypoints = state.filter_waypoint_state_by_sources_and_room(
        state.waypoints,
        selected_sources,
        selected_room,
    )

    room_digests = {
        "players": state.state_digest(selected_players),
        "entities": state.state_digest(selected_entities),
        "waypoints": state.state_digest(selected_waypoints),
    }

    return JSONResponse({
        "server_time": current_time,
        "players": dict(state.players),
        "entities": dict(state.entities),
        "waypoints": dict(state.waypoints),
        "playerMarks": dict(state.player_marks),
        "tabState": state.build_admin_tab_snapshot(selected_room),
        "connections": list(state.connections.keys()),
        "connections_count": len(state.connections),
        "activeRooms": active_rooms,
        "connectionsByRoom": connections_by_room,
        "requestedRoomCode": requested_room,
        "selectedRoomCode": selected_room,
        "roomView": {
            "roomCode": selected_room,
            "connections": sorted(selected_sources),
            "connections_count": len(selected_sources),
            "players": dict(selected_players),
            "entities": dict(selected_entities),
            "waypoints": dict(selected_waypoints),
            "tabState": state.build_admin_tab_snapshot(selected_room),
            "digests": room_digests,
        },
        "broadcastHz": state.broadcast_hz,
        "digests": state.build_digests(),
    })


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8765, ws_per_message_deflate=True)
