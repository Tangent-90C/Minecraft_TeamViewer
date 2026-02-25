import hashlib
import json
import math
import time
from typing import Dict, Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, ConfigDict, Field


class PlayerData(BaseModel):
    x: float = Field(..., description="X坐标")
    y: float = Field(..., description="Y坐标")
    z: float = Field(..., description="Z坐标")
    vx: float = Field(default=0, description="X方向速度")
    vy: float = Field(default=0, description="Y方向速度")
    vz: float = Field(default=0, description="Z方向速度")
    dimension: str = Field(..., description="维度ID")
    playerName: Optional[str] = Field(None, description="玩家名称")
    playerUUID: Optional[str] = Field(None, description="玩家UUID")
    health: float = Field(default=0, ge=0, description="当前生命值")
    maxHealth: float = Field(default=20, ge=0, description="最大生命值")
    armor: float = Field(default=0, ge=0, description="护甲值")
    width: float = Field(default=0.6, gt=0, description="碰撞箱宽度")
    height: float = Field(default=1.8, gt=0, description="碰撞箱高度")

    model_config = ConfigDict(extra="ignore")


class EntityData(BaseModel):
    x: float = Field(..., description="X坐标")
    y: float = Field(..., description="Y坐标")
    z: float = Field(..., description="Z坐标")
    vx: float = Field(default=0, description="X方向速度")
    vy: float = Field(default=0, description="Y方向速度")
    vz: float = Field(default=0, description="Z方向速度")
    dimension: str = Field(..., description="维度ID")
    entityType: Optional[str] = Field(None, description="实体类型")
    entityName: Optional[str] = Field(None, description="实体名称")
    width: float = Field(default=0.6, ge=0, description="碰撞箱宽度")
    height: float = Field(default=1.8, ge=0, description="碰撞箱高度")

    model_config = ConfigDict(extra="ignore")


class WaypointData(BaseModel):
    x: float = Field(..., description="X坐标")
    y: float = Field(..., description="Y坐标")
    z: float = Field(..., description="Z坐标")
    dimension: str = Field(..., description="维度ID")
    name: str = Field(..., description="路标名称")
    symbol: Optional[str] = Field("W", description="路标符号")
    color: int = Field(default=5635925, description="路标颜色")
    ownerId: Optional[str] = Field(None, description="创建者UUID")
    ownerName: Optional[str] = Field(None, description="创建者名称")
    createdAt: Optional[int] = Field(None, description="创建时间戳(ms)")
    ttlSeconds: Optional[int] = Field(None, ge=5, le=86400, description="路标超时秒数")
    waypointKind: Optional[str] = Field(None, description="路标类型: quick/manual")
    replaceOldQuick: Optional[bool] = Field(None, description="是否替换同玩家旧快捷报点")
    maxQuickMarks: Optional[int] = Field(None, ge=1, le=100, description="快捷报点最多保留数量")
    targetType: Optional[str] = Field(None, description="命中目标类型:block/entity")
    targetEntityId: Optional[str] = Field(None, description="命中实体UUID")
    targetEntityType: Optional[str] = Field(None, description="命中实体类型")
    targetEntityName: Optional[str] = Field(None, description="命中实体名称")

    model_config = ConfigDict(extra="ignore")


# 当前“已仲裁后的最终视图”，用于直接对外广播。
players: Dict[str, dict] = {}
entities: Dict[str, dict] = {}
waypoints: Dict[str, dict] = {}

# 原始上报池：object_id -> source_id -> state_node
# 每个来源都单独保存，不在写入时丢弃其他来源。
player_reports: Dict[str, Dict[str, dict]] = {}
entity_reports: Dict[str, Dict[str, dict]] = {}
waypoint_reports: Dict[str, Dict[str, dict]] = {}

connections: Dict[str, WebSocket] = {}
connection_caps: Dict[str, dict] = {}
admin_connections: Dict[str, WebSocket] = {}

# 记录每个 object 当前选中的来源，用于“来源粘性”避免频繁切源抖动。
player_selected_sources: Dict[str, str] = {}
entity_selected_sources: Dict[str, str] = {}
waypoint_selected_sources: Dict[str, str] = {}

PLAYER_TIMEOUT = 5
ENTITY_TIMEOUT = 5
WAYPOINT_TIMEOUT = 120
ONLINE_OWNER_TIMEOUT_MULTIPLIER = 8
# 仅当候选来源相对当前来源领先超过阈值，才允许切源。
SOURCE_SWITCH_THRESHOLD_SEC = 0.35

PROTOCOL_V2 = 2
DIGEST_INTERVAL_SEC = 10

revision = 0

app = FastAPI()
app.mount("/admin", StaticFiles(directory="static", html=True), name="admin")


def next_revision() -> int:
    global revision
    revision += 1
    return revision


def compact_state_map(state_map: Dict[str, dict]) -> Dict[str, dict]:
    return {sid: node.get("data", {}) for sid, node in state_map.items()}


def canonical_number(value: float) -> str:
    if not math.isfinite(value):
        return "null"
    rounded = round(float(value), 6)
    text = f"{rounded:.6f}".rstrip("0").rstrip(".")
    if text in ("", "-0"):
        return "0"
    return text


def canonical_value(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return canonical_number(value)
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    if isinstance(value, dict):
        items = []
        for key in sorted(value.keys(), key=lambda item: str(item)):
            key_json = json.dumps(str(key), ensure_ascii=False, separators=(",", ":"))
            items.append(f"{key_json}:{canonical_value(value[key])}")
        return "{" + ",".join(items) + "}"
    if isinstance(value, list):
        return "[" + ",".join(canonical_value(item) for item in value) + "]"

    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    except TypeError:
        return json.dumps(str(value), ensure_ascii=False, separators=(",", ":"))


def state_digest(state_map: Dict[str, dict]) -> str:
    lines = []
    for node_id in sorted(state_map.keys()):
        node = state_map.get(node_id, {})
        data = node.get("data", {}) if isinstance(node, dict) else {}
        node_json = json.dumps(str(node_id), ensure_ascii=False, separators=(",", ":"))
        lines.append(f"{node_json}:{canonical_value(data)}")

    raw = "\n".join(lines)
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]


def build_digests() -> dict:
    return {
        "players": state_digest(players),
        "entities": state_digest(entities),
        "waypoints": state_digest(waypoints),
    }


def make_empty_patch() -> dict:
    return {
        "players": {"upsert": {}, "delete": []},
        "entities": {"upsert": {}, "delete": []},
        "waypoints": {"upsert": {}, "delete": []},
    }


def has_patch_changes(patch: dict) -> bool:
    for scope in ("players", "entities", "waypoints"):
        if patch[scope]["upsert"] or patch[scope]["delete"]:
            return True
    return False


def merge_patch(base: dict, extra: dict) -> None:
    for scope in ("players", "entities", "waypoints"):
        base[scope]["upsert"].update(extra[scope]["upsert"])
        base[scope]["delete"].extend(extra[scope]["delete"])


def compute_field_delta(old_data: Optional[dict], new_data: dict) -> dict:
    if old_data is None:
        return dict(new_data)

    delta = {}
    for key, value in new_data.items():
        if old_data.get(key) != value:
            delta[key] = value
    return delta


def merge_patch_and_validate(model_cls, existing_node: Optional[dict], patch_data: dict) -> dict:
    merged = {}
    if existing_node and isinstance(existing_node.get("data"), dict):
        merged.update(existing_node["data"])
    if isinstance(patch_data, dict):
        merged.update(patch_data)
    validated = model_cls(**merged)
    return validated.model_dump()


def build_state_node(submit_player_id: Optional[str], current_time: float, normalized: dict) -> dict:
    """标准化单条来源上报结构。"""
    return {
        "timestamp": current_time,
        "submitPlayerId": submit_player_id,
        "data": normalized,
    }


def upsert_report(report_map: Dict[str, Dict[str, dict]], object_id: str, source_id: Optional[str], node: dict) -> None:
    """写入/覆盖某 object 在某来源下的最新上报。"""
    source_key = source_id if isinstance(source_id, str) else ""
    source_bucket = report_map.setdefault(object_id, {})
    source_bucket[source_key] = node


def delete_report(report_map: Dict[str, Dict[str, dict]], object_id: str, source_id: Optional[str]) -> bool:
    """删除某 object 在某来源下的上报；若 bucket 为空则清理 object。"""
    if object_id not in report_map:
        return False

    source_bucket = report_map[object_id]
    source_key = source_id if isinstance(source_id, str) else ""
    if source_key not in source_bucket:
        return False

    del source_bucket[source_key]
    if not source_bucket:
        del report_map[object_id]
    return True


def node_timestamp(node: Optional[dict]) -> float:
    """安全读取 node 时间戳，异常数据统一按 0 处理。"""
    if not isinstance(node, dict):
        return 0.0
    value = node.get("timestamp")
    if not isinstance(value, (int, float)):
        return 0.0
    return float(value)


def resolve_report_map(
    report_map: Dict[str, Dict[str, dict]],
    selected_sources: Dict[str, str],
    switch_threshold_sec: float,
    prefer_object_id_source: bool = False,
) -> Dict[str, dict]:
    """
    从多来源上报池计算“最终视图”。

    仲裁顺序：
    1) 基础候选：时间戳最新（并发相同时间戳时按 source_id 保持稳定）。
    2) 可选偏好：如果 object_id 对应来源存在（常用于玩家本人），且不明显落后则优先它。
    3) 来源粘性：若上一帧选中的来源仍在且差距不大，保持原来源避免来回切换。
    """
    resolved: Dict[str, dict] = {}
    next_selected_sources: Dict[str, str] = {}

    for object_id, source_bucket in report_map.items():
        if not isinstance(source_bucket, dict) or not source_bucket:
            continue

        valid_bucket: Dict[str, dict] = {
            source_id: node
            for source_id, node in source_bucket.items()
            if isinstance(node, dict)
        }
        if not valid_bucket:
            continue

        best_source_id = None
        best_node = None
        best_timestamp = float("-inf")
        for source_id, node in valid_bucket.items():
            timestamp_value = node_timestamp(node)

            if timestamp_value > best_timestamp:
                best_source_id = source_id
                best_node = node
                best_timestamp = timestamp_value
                continue

            if timestamp_value == best_timestamp:
                current_best_key = str(best_source_id) if best_source_id is not None else ""
                current_key = str(source_id)
                if current_key < current_best_key:
                    best_source_id = source_id
                    best_node = node

        chosen_source_id = best_source_id
        chosen_node = best_node

        # 玩家对象可优先本人来源（object_id == source_id）。
        preferred_source = str(object_id) if prefer_object_id_source else None
        if preferred_source and preferred_source in valid_bucket:
            preferred_node = valid_bucket[preferred_source]
            preferred_ts = node_timestamp(preferred_node)
            if best_timestamp - preferred_ts <= switch_threshold_sec:
                chosen_source_id = preferred_source
                chosen_node = preferred_node

        # 粘性策略：上一轮来源若仍可用且不明显落后，则继续沿用。
        previous_source = selected_sources.get(object_id)
        if previous_source in valid_bucket:
            previous_node = valid_bucket[previous_source]
            previous_ts = node_timestamp(previous_node)
            chosen_ts = node_timestamp(chosen_node)
            if chosen_ts - previous_ts <= switch_threshold_sec:
                chosen_source_id = previous_source
                chosen_node = previous_node

        if chosen_node is not None and chosen_source_id is not None:
            resolved[object_id] = chosen_node
            next_selected_sources[object_id] = chosen_source_id

    selected_sources.clear()
    selected_sources.update(next_selected_sources)

    return resolved


def compute_scope_patch(old_map: Dict[str, dict], new_map: Dict[str, dict]) -> dict:
    scope_patch = {"upsert": {}, "delete": []}

    for object_id in old_map.keys() - new_map.keys():
        scope_patch["delete"].append(object_id)

    for object_id, new_node in new_map.items():
        old_node = old_map.get(object_id)
        old_data = old_node.get("data") if isinstance(old_node, dict) else None
        new_data = new_node.get("data") if isinstance(new_node, dict) else None
        if not isinstance(new_data, dict):
            new_data = {}
        delta = compute_field_delta(old_data if isinstance(old_data, dict) else None, new_data)
        if delta:
            scope_patch["upsert"][object_id] = delta

    scope_patch["delete"].sort()
    return scope_patch


def refresh_resolved_states() -> dict:
    """刷新三类最终视图，并返回相对上一帧的 patch。"""
    global players, entities, waypoints

    old_players = dict(players)
    old_entities = dict(entities)
    old_waypoints = dict(waypoints)

    players = resolve_report_map(
        player_reports,
        player_selected_sources,
        SOURCE_SWITCH_THRESHOLD_SEC,
        prefer_object_id_source=True,
    )
    entities = resolve_report_map(
        entity_reports,
        entity_selected_sources,
        SOURCE_SWITCH_THRESHOLD_SEC,
        prefer_object_id_source=False,
    )
    waypoints = resolve_report_map(
        waypoint_reports,
        waypoint_selected_sources,
        SOURCE_SWITCH_THRESHOLD_SEC,
        prefer_object_id_source=False,
    )

    return {
        "players": compute_scope_patch(old_players, players),
        "entities": compute_scope_patch(old_entities, entities),
        "waypoints": compute_scope_patch(old_waypoints, waypoints),
    }


def mark_player_capability(player_id: str, protocol_version: int, delta_enabled: bool) -> None:
    connection_caps[player_id] = {
        "protocol": protocol_version,
        "delta": bool(protocol_version >= PROTOCOL_V2 and delta_enabled),
        "lastDigestSent": 0.0,
    }


def is_delta_client(player_id: str) -> bool:
    caps = connection_caps.get(player_id)
    if not caps:
        return False
    return bool(caps.get("delta", False))


async def send_snapshot_full_to_player(player_id: str) -> None:
    ws = connections.get(player_id)
    if ws is None:
        return
    message = {
        "type": "snapshot_full",
        "rev": revision,
        "players": compact_state_map(players),
        "entities": compact_state_map(entities),
        "waypoints": compact_state_map(waypoints),
    }
    await ws.send_text(json.dumps(message, separators=(",", ":")))


async def maybe_send_digest(player_id: str) -> None:
    ws = connections.get(player_id)
    caps = connection_caps.get(player_id)
    if ws is None or caps is None or not caps.get("delta"):
        return

    now = time.time()
    if now - float(caps.get("lastDigestSent", 0.0)) < DIGEST_INTERVAL_SEC:
        return

    caps["lastDigestSent"] = now
    message = {
        "type": "digest",
        "rev": revision,
        "hashes": build_digests(),
    }
    await ws.send_text(json.dumps(message, separators=(",", ":")))


def cleanup_timeouts() -> None:
    """按来源维度清理超时上报，保留仍活跃的来源数据。"""
    current_time = time.time()

    def is_owner_online(node: dict) -> bool:
        owner_id = node.get("submitPlayerId") if isinstance(node, dict) else None
        return isinstance(owner_id, str) and owner_id in connections

    def effective_timeout(base_timeout: int, node: dict) -> int:
        if is_owner_online(node):
            return base_timeout * ONLINE_OWNER_TIMEOUT_MULTIPLIER
        return base_timeout

    def effective_waypoint_timeout(node: dict) -> int:
        if not isinstance(node, dict):
            return WAYPOINT_TIMEOUT
        data = node.get("data")
        if not isinstance(data, dict):
            return WAYPOINT_TIMEOUT
        ttl = data.get("ttlSeconds")
        if isinstance(ttl, (int, float)):
            ttl_int = int(ttl)
            if ttl_int < 5:
                return 5
            return min(ttl_int, 86400)
        return WAYPOINT_TIMEOUT

    def cleanup_report_map(report_map: Dict[str, Dict[str, dict]], timeout_resolver) -> None:
        # 两层清理：先清理来源，再清理空 object bucket。
        for object_id in list(report_map.keys()):
            source_bucket = report_map.get(object_id)
            if not isinstance(source_bucket, dict):
                del report_map[object_id]
                continue

            for source_id in list(source_bucket.keys()):
                node = source_bucket.get(source_id)
                if not isinstance(node, dict):
                    del source_bucket[source_id]
                    continue
                timestamp = node.get("timestamp")
                if not isinstance(timestamp, (int, float)):
                    del source_bucket[source_id]
                    continue

                timeout_seconds = timeout_resolver(node)
                if current_time - float(timestamp) > timeout_seconds:
                    del source_bucket[source_id]

            if not source_bucket:
                del report_map[object_id]

    cleanup_report_map(player_reports, lambda node: effective_timeout(PLAYER_TIMEOUT, node))
    cleanup_report_map(entity_reports, lambda node: effective_timeout(ENTITY_TIMEOUT, node))
    cleanup_report_map(waypoint_reports, effective_waypoint_timeout)


async def broadcast_snapshot() -> None:
    current_time = time.time()
    snapshot_data = {
        "server_time": current_time,
        "players": dict(players),
        "entities": dict(entities),
        "waypoints": dict(waypoints),
        "connections": list(connections.keys()),
        "connections_count": len(connections),
        "revision": revision,
    }

    try:
        message = json.dumps(snapshot_data, separators=(",", ":"))
    except Exception as e:
        print(f"Error serializing snapshot data: {e}")
        return

    disconnected = []
    for admin_id, ws in list(admin_connections.items()):
        try:
            await ws.send_text(message)
        except Exception as e:
            print(f"Error sending snapshot to admin {admin_id}: {e}")
            disconnected.append(admin_id)

    for admin_id in disconnected:
        if admin_id in admin_connections:
            del admin_connections[admin_id]


async def broadcast_legacy_positions() -> None:
    message_data = {
        "type": "positions",
        "players": dict(players),
        "entities": dict(entities),
        "waypoints": dict(waypoints),
    }

    try:
        message = json.dumps(message_data, separators=(",", ":"))
    except Exception as e:
        print(f"Error serializing legacy positions data: {e}")
        return

    disconnected = []
    for player_uuid, ws in list(connections.items()):
        if is_delta_client(player_uuid):
            continue
        try:
            await ws.send_text(message)
        except Exception as e:
            print(f"Error sending legacy message to player {player_uuid}: {e}")
            disconnected.append(player_uuid)

    for player_uuid in disconnected:
        remove_connection(player_uuid)


async def broadcast_updates(force_full_to_delta: bool = False) -> None:
    """
    统一广播入口。

    流程：清理超时 -> 聚合仲裁 -> 计算 patch -> 按客户端能力广播。
    """
    cleanup_timeouts()
    changes = refresh_resolved_states()

    changed = has_patch_changes(changes)
    if changed:
        rev = next_revision()
    else:
        rev = revision

    disconnected = []
    for player_id, ws in list(connections.items()):
        try:
            if is_delta_client(player_id):
                if force_full_to_delta:
                    full_msg = {
                        "type": "snapshot_full",
                        "rev": rev,
                        "players": compact_state_map(players),
                        "entities": compact_state_map(entities),
                        "waypoints": compact_state_map(waypoints),
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

                await maybe_send_digest(player_id)
        except Exception as e:
            print(f"Error sending delta update to player {player_id}: {e}")
            disconnected.append(player_id)

    for player_id in disconnected:
        remove_connection(player_id)

    if changed:
        await broadcast_legacy_positions()

    await broadcast_snapshot()


def remove_connection(player_id: str) -> None:
    """连接断开后，移除该来源在所有上报池中的数据。"""

    if player_id in connections:
        del connections[player_id]
    if player_id in connection_caps:
        del connection_caps[player_id]

    def remove_source_reports(report_map: Dict[str, Dict[str, dict]]) -> None:
        for object_id in list(report_map.keys()):
            source_bucket = report_map.get(object_id)
            if not isinstance(source_bucket, dict):
                del report_map[object_id]
                continue
            if player_id in source_bucket:
                del source_bucket[player_id]
            if not source_bucket:
                del report_map[object_id]

    remove_source_reports(player_reports)
    remove_source_reports(entity_reports)
    remove_source_reports(waypoint_reports)


@app.websocket("/adminws")
async def admin_ws(websocket: WebSocket):
    await websocket.accept()
    admin_id = str(id(websocket))
    admin_connections[admin_id] = websocket
    try:
        await broadcast_snapshot()
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    except Exception as e:
        print(f"Admin websocket error: {e}")
    finally:
        if admin_id in admin_connections:
            del admin_connections[admin_id]


@app.websocket("/playeresp")
async def websocket_endpoint(websocket: WebSocket):
    """
    客户端主通道。

    关键约定：
    - 写入阶段只更新“来源上报池”；
    - 下发阶段统一走 broadcast_updates 做聚合仲裁。
    """
    await websocket.accept()
    submit_player_id = None

    try:
        while True:
            message = await websocket.receive_text()
            try:
                data = json.loads(message)
            except json.JSONDecodeError as e:
                print(f"Error decoding JSON message: {e}")
                continue

            submit_player_id = data.get("submitPlayerId")
            message_type = data.get("type")

            if message_type == "handshake":
                if submit_player_id:
                    connections[submit_player_id] = websocket
                    client_protocol = int(data.get("protocolVersion", 1))
                    client_delta = bool(data.get("supportsDelta", False))
                    mark_player_capability(submit_player_id, client_protocol, client_delta)

                    print(f"Client {submit_player_id} connected (protocol {client_protocol})")
                    ack = {
                        "type": "handshake_ack",
                        "ready": True,
                        "protocolVersion": PROTOCOL_V2,
                        "deltaEnabled": is_delta_client(submit_player_id),
                        "digestIntervalSec": DIGEST_INTERVAL_SEC,
                        "rev": revision,
                    }
                    await websocket.send_text(json.dumps(ack, separators=(",", ":")))

                    await broadcast_updates(force_full_to_delta=is_delta_client(submit_player_id))
                continue

            if submit_player_id and submit_player_id not in connections:
                connections[submit_player_id] = websocket
                mark_player_capability(submit_player_id, 1, False)
                print(f"Client {submit_player_id} connected (legacy)")

            if message_type == "players_update":
                # 全量更新：仅覆盖当前来源提交的这些玩家数据。
                current_time = time.time()
                for pid, player_data in data.get("players", {}).items():
                    try:
                        validated_data = PlayerData(**player_data)
                        normalized = validated_data.model_dump()
                        node = build_state_node(submit_player_id, current_time, normalized)
                        upsert_report(player_reports, pid, submit_player_id, node)
                    except Exception as e:
                        print(f"Error validating player data for {pid}: {e}")

                await broadcast_updates()
                continue

            if message_type == "players_patch":
                # 增量更新：基于“当前来源已有快照”合并后校验。
                current_time = time.time()
                upsert = data.get("upsert", {})
                delete = data.get("delete", [])

                for pid, player_data in upsert.items():
                    try:
                        source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                        existing_node = player_reports.get(pid, {}).get(source_key)
                        normalized = merge_patch_and_validate(PlayerData, existing_node, player_data)
                        node = build_state_node(submit_player_id, current_time, normalized)
                        upsert_report(player_reports, pid, submit_player_id, node)
                    except Exception as e:
                        print(f"Error validating player patch for {pid}: {e}")

                if isinstance(delete, list):
                    for pid in delete:
                        if not isinstance(pid, str):
                            continue
                        delete_report(player_reports, pid, submit_player_id)

                await broadcast_updates()
                continue

            if message_type == "entities_update":
                # 语义保持旧协议：当前来源的实体列表视为“本轮全量”。
                # 因此先删除该来源旧实体，再写入新实体。
                current_time = time.time()
                player_entities = data.get("entities", {})
                source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                for entity_id in list(entity_reports.keys()):
                    source_bucket = entity_reports.get(entity_id, {})
                    if source_key in source_bucket:
                        delete_report(entity_reports, entity_id, submit_player_id)

                for entity_id, entity_data in player_entities.items():
                    try:
                        validated_data = EntityData(**entity_data)
                        normalized = validated_data.model_dump()
                        node = build_state_node(submit_player_id, current_time, normalized)
                        upsert_report(entity_reports, entity_id, submit_player_id, node)
                    except Exception as e:
                        print(f"Error validating entity data for {entity_id}: {e}")

                await broadcast_updates()
                continue

            if message_type == "entities_patch":
                # 增量实体更新，仅影响当前来源的数据桶。
                current_time = time.time()
                upsert = data.get("upsert", {})
                delete = data.get("delete", [])

                for entity_id, entity_data in upsert.items():
                    try:
                        source_key = submit_player_id if isinstance(submit_player_id, str) else ""
                        existing_node = entity_reports.get(entity_id, {}).get(source_key)
                        normalized = merge_patch_and_validate(EntityData, existing_node, entity_data)
                        node = build_state_node(submit_player_id, current_time, normalized)
                        upsert_report(entity_reports, entity_id, submit_player_id, node)
                    except Exception as e:
                        print(f"Error validating entity patch for {entity_id}: {e}")

                if isinstance(delete, list):
                    for entity_id in delete:
                        if not isinstance(entity_id, str):
                            continue
                        delete_report(entity_reports, entity_id, submit_player_id)

                await broadcast_updates()
                continue

            if message_type == "waypoints_update":
                # 路标上报；quick 时按 maxQuickMarks 限制“当前来源”的旧 quick 数量。
                current_time = time.time()
                player_waypoints = data.get("waypoints", {})
                for waypoint_id, waypoint_data in player_waypoints.items():
                    try:
                        validated_data = WaypointData(**waypoint_data)
                        normalized = validated_data.model_dump()

                        if normalized.get("waypointKind") == "quick":
                            max_quick_marks = normalized.get("maxQuickMarks")
                            if isinstance(max_quick_marks, (int, float)):
                                max_quick_marks = max(1, min(int(max_quick_marks), 100))
                            elif bool(normalized.get("replaceOldQuick")):
                                # 兼容旧客户端：replaceOldQuick=true 等价于最多保留 1 个。
                                max_quick_marks = 1
                            else:
                                max_quick_marks = None

                            if max_quick_marks is not None:
                                old_quick_waypoints = [
                                    (wid, source_bucket[submit_player_id])
                                    for wid, source_bucket in list(waypoint_reports.items())
                                    if wid != waypoint_id
                                    and isinstance(source_bucket, dict)
                                    and submit_player_id in source_bucket
                                    and isinstance(source_bucket[submit_player_id], dict)
                                    and isinstance(source_bucket[submit_player_id].get("data"), dict)
                                    and source_bucket[submit_player_id]["data"].get("waypointKind") == "quick"
                                ]

                                remove_count = len(old_quick_waypoints) - max_quick_marks + 1
                                if remove_count > 0:
                                    old_quick_waypoints.sort(key=lambda item: node_timestamp(item[1]))
                                    for old_id, _ in old_quick_waypoints[:remove_count]:
                                        delete_report(waypoint_reports, old_id, submit_player_id)

                        node = build_state_node(submit_player_id, current_time, normalized)
                        upsert_report(waypoint_reports, waypoint_id, submit_player_id, node)
                    except Exception as e:
                        print(f"Error validating waypoint data for {waypoint_id}: {e}")

                await broadcast_updates()
                continue

            if message_type == "waypoints_delete":
                waypoint_ids = data.get("waypointIds", [])
                current_time = time.time()
                if not isinstance(waypoint_ids, list):
                    waypoint_ids = []

                for waypoint_id in waypoint_ids:
                    if not isinstance(waypoint_id, str):
                        continue
                    delete_report(waypoint_reports, waypoint_id, submit_player_id)

                await broadcast_updates()
                continue

            if message_type == "resync_req" and submit_player_id:
                try:
                    await send_snapshot_full_to_player(submit_player_id)
                except Exception as e:
                    print(f"Error sending snapshot_full to {submit_player_id}: {e}")
                continue

    except WebSocketDisconnect:
        pass
    except Exception as e:
        print(f"Error handling player message: {e}")
    finally:
        if submit_player_id:
            remove_connection(submit_player_id)
            print(f"Client {submit_player_id} disconnected")
            await broadcast_updates()


@app.get("/health")
async def health_check():
    return JSONResponse({"status": "ok"})


@app.get("/snapshot")
async def snapshot():
    current_time = time.time()
    return JSONResponse({
        "server_time": current_time,
        "players": dict(players),
        "entities": dict(entities),
        "waypoints": dict(waypoints),
        "connections": list(connections.keys()),
        "connections_count": len(connections),
        "revision": revision,
        "digests": build_digests(),
    })


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8765, ws_per_message_deflate=True)
