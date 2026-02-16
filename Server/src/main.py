import hashlib
import json
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
    width: float = Field(default=0.6, gt=0, description="碰撞箱宽度")
    height: float = Field(default=1.8, gt=0, description="碰撞箱高度")

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

    model_config = ConfigDict(extra="ignore")


players: Dict[str, dict] = {}
entities: Dict[str, dict] = {}
waypoints: Dict[str, dict] = {}

connections: Dict[str, WebSocket] = {}
connection_caps: Dict[str, dict] = {}
admin_connections: Dict[str, WebSocket] = {}

PLAYER_TIMEOUT = 5
ENTITY_TIMEOUT = 5
ONLINE_OWNER_TIMEOUT_MULTIPLIER = 8

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


def stable_hash(payload: dict) -> str:
    raw = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]


def build_digests() -> dict:
    return {
        "players": stable_hash(compact_state_map(players)),
        "entities": stable_hash(compact_state_map(entities)),
        "waypoints": stable_hash(compact_state_map(waypoints)),
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


async def cleanup_timeouts() -> dict:
    current_time = time.time()
    patch = make_empty_patch()

    def is_owner_online(node: dict) -> bool:
        owner_id = node.get("submitPlayerId") if isinstance(node, dict) else None
        return isinstance(owner_id, str) and owner_id in connections

    def effective_timeout(base_timeout: int, node: dict) -> int:
        if is_owner_online(node):
            return base_timeout * ONLINE_OWNER_TIMEOUT_MULTIPLIER
        return base_timeout

    expired_players = [
        pid for pid, pdata in list(players.items())
        if current_time - pdata["timestamp"] > effective_timeout(PLAYER_TIMEOUT, pdata)
    ]
    for pid in expired_players:
        if pid in players:
            del players[pid]
            patch["players"]["delete"].append(pid)

    expired_entities = [
        eid for eid, edata in list(entities.items())
        if current_time - edata["timestamp"] > effective_timeout(ENTITY_TIMEOUT, edata)
    ]
    for eid in expired_entities:
        if eid in entities:
            del entities[eid]
            patch["entities"]["delete"].append(eid)

    return patch


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


async def broadcast_updates(changes: dict, force_full_to_delta: bool = False) -> None:
    timeout_patch = await cleanup_timeouts()
    merge_patch(changes, timeout_patch)

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


def remove_connection(player_id: str) -> dict:
    patch = make_empty_patch()

    if player_id in connections:
        del connections[player_id]
    if player_id in connection_caps:
        del connection_caps[player_id]

    players_to_remove = [
        pid for pid, pdata in list(players.items())
        if pdata.get("submitPlayerId") == player_id
    ]
    for pid in players_to_remove:
        del players[pid]
        patch["players"]["delete"].append(pid)

    entities_to_remove = [
        eid for eid, edata in list(entities.items())
        if edata.get("submitPlayerId") == player_id
    ]
    for eid in entities_to_remove:
        del entities[eid]
        patch["entities"]["delete"].append(eid)

    waypoints_to_remove = [
        wid for wid, wdata in list(waypoints.items())
        if wdata.get("submitPlayerId") == player_id
    ]
    for wid in waypoints_to_remove:
        del waypoints[wid]
        patch["waypoints"]["delete"].append(wid)

    return patch


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

                    changes = make_empty_patch()
                    await broadcast_updates(changes, force_full_to_delta=is_delta_client(submit_player_id))
                continue

            if submit_player_id and submit_player_id not in connections:
                connections[submit_player_id] = websocket
                mark_player_capability(submit_player_id, 1, False)
                print(f"Client {submit_player_id} connected (legacy)")

            changes = make_empty_patch()

            if message_type == "players_update":
                current_time = time.time()
                for pid, player_data in data.get("players", {}).items():
                    try:
                        existing_node = players.get(pid)
                        validated_data = PlayerData(**player_data)
                        normalized = validated_data.model_dump()
                        players[pid] = {
                            "timestamp": current_time,
                            "submitPlayerId": submit_player_id,
                            "data": normalized,
                        }
                        field_delta = compute_field_delta(
                            existing_node.get("data") if existing_node else None,
                            normalized,
                        )
                        if field_delta:
                            changes["players"]["upsert"][pid] = field_delta
                    except Exception as e:
                        print(f"Error validating player data for {pid}: {e}")

                await broadcast_updates(changes)
                continue

            if message_type == "players_patch":
                current_time = time.time()
                upsert = data.get("upsert", {})
                delete = data.get("delete", [])

                for pid, player_data in upsert.items():
                    try:
                        existing_node = players.get(pid)
                        old_data = existing_node.get("data") if existing_node else None
                        normalized = merge_patch_and_validate(PlayerData, existing_node, player_data)
                        players[pid] = {
                            "timestamp": current_time,
                            "submitPlayerId": submit_player_id,
                            "data": normalized,
                        }
                        field_delta = compute_field_delta(old_data, normalized)
                        if field_delta:
                            changes["players"]["upsert"][pid] = field_delta
                    except Exception as e:
                        print(f"Error validating player patch for {pid}: {e}")

                if isinstance(delete, list):
                    for pid in delete:
                        if isinstance(pid, str) and pid in players:
                            del players[pid]
                            changes["players"]["delete"].append(pid)

                await broadcast_updates(changes)
                continue

            if message_type == "entities_update":
                current_time = time.time()
                player_entities = data.get("entities", {})
                entities_to_remove = [
                    eid for eid, edata in list(entities.items())
                    if edata.get("submitPlayerId") == submit_player_id
                ]
                for eid in entities_to_remove:
                    del entities[eid]
                    changes["entities"]["delete"].append(eid)

                for entity_id, entity_data in player_entities.items():
                    try:
                        existing_node = entities.get(entity_id)
                        validated_data = EntityData(**entity_data)
                        normalized = validated_data.model_dump()
                        entities[entity_id] = {
                            "timestamp": current_time,
                            "submitPlayerId": submit_player_id,
                            "data": normalized,
                        }
                        field_delta = compute_field_delta(
                            existing_node.get("data") if existing_node else None,
                            normalized,
                        )
                        if field_delta:
                            changes["entities"]["upsert"][entity_id] = field_delta
                    except Exception as e:
                        print(f"Error validating entity data for {entity_id}: {e}")

                await broadcast_updates(changes)
                continue

            if message_type == "entities_patch":
                current_time = time.time()
                upsert = data.get("upsert", {})
                delete = data.get("delete", [])

                for entity_id, entity_data in upsert.items():
                    try:
                        existing_node = entities.get(entity_id)
                        old_data = existing_node.get("data") if existing_node else None
                        normalized = merge_patch_and_validate(EntityData, existing_node, entity_data)
                        entities[entity_id] = {
                            "timestamp": current_time,
                            "submitPlayerId": submit_player_id,
                            "data": normalized,
                        }
                        field_delta = compute_field_delta(old_data, normalized)
                        if field_delta:
                            changes["entities"]["upsert"][entity_id] = field_delta
                    except Exception as e:
                        print(f"Error validating entity patch for {entity_id}: {e}")

                if isinstance(delete, list):
                    for entity_id in delete:
                        if isinstance(entity_id, str) and entity_id in entities:
                            del entities[entity_id]
                            changes["entities"]["delete"].append(entity_id)

                await broadcast_updates(changes)
                continue

            if message_type == "waypoints_update":
                current_time = time.time()
                player_waypoints = data.get("waypoints", {})
                for waypoint_id, waypoint_data in player_waypoints.items():
                    try:
                        validated_data = WaypointData(**waypoint_data)
                        waypoints[waypoint_id] = {
                            "timestamp": current_time,
                            "submitPlayerId": submit_player_id,
                            "data": validated_data.model_dump(),
                        }
                        changes["waypoints"]["upsert"][waypoint_id] = validated_data.model_dump()
                    except Exception as e:
                        print(f"Error validating waypoint data for {waypoint_id}: {e}")

                await broadcast_updates(changes)
                continue

            if message_type == "waypoints_delete":
                waypoint_ids = data.get("waypointIds", [])
                if not isinstance(waypoint_ids, list):
                    waypoint_ids = []

                for waypoint_id in waypoint_ids:
                    if not isinstance(waypoint_id, str):
                        continue
                    if waypoint_id in waypoints:
                        del waypoints[waypoint_id]
                        changes["waypoints"]["delete"].append(waypoint_id)

                await broadcast_updates(changes)
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
            disconnected_patch = remove_connection(submit_player_id)
            print(f"Client {submit_player_id} disconnected")
            await broadcast_updates(disconnected_patch)


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
