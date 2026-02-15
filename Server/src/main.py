import json
import time
import gzip
from typing import Dict, Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, ConfigDict, Field

# 玩家数据模型
class PlayerData(BaseModel):
    """玩家业务数据模型"""
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

    model_config = ConfigDict(
        extra="ignore"
    )


class EntityData(BaseModel):
    """实体数据模型"""
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

    model_config = ConfigDict(
        extra="ignore"
    )


# 存储玩家数据和连接
players: Dict[str, dict] = {}
connections: Dict[str, WebSocket] = {}
# 后台页面连接
admin_connections: Dict[str, WebSocket] = {}

# 存储每个连接的配置（是否启用压缩）
connection_config: Dict[str, dict] = {}  # {player_uuid: {"enable_compression": bool}}

# 存储实体数据
entities: Dict[str, dict] = {}

# 存储玩家数据的时间限制（秒）
PLAYER_TIMEOUT = 5
ENTITY_TIMEOUT = 5

app = FastAPI()

# 挂载静态页面目录到 /admin
app.mount("/admin", StaticFiles(directory="static", html=True), name="admin")


async def broadcast_positions():
    # 清理过期数据
    current_time = time.time()
    expired_players = [
        pid for pid, pdata in list(players.items())
        if current_time - pdata["timestamp"] > PLAYER_TIMEOUT
    ]
    for pid in expired_players:
        if pid in players:
            del players[pid]

    # 清理过期实体数据
    expired_entities = [
        eid for eid, edata in list(entities.items())
        if current_time - edata["timestamp"] > ENTITY_TIMEOUT
    ]
    for eid in expired_entities:
        del entities[eid]

    # 准备要发送的数据
    message_data = {
        "type": "positions",
        "players": dict(players),
        "entities": dict(entities)
    }

    try:
        message = json.dumps(message_data, separators=(",", ":"))
    except Exception as e:
        print(f"Error serializing message data: {e}")
        return

    disconnected = []
    for player_uuid, ws in list(connections.items()):
        try:
            # 检查该客户端的压缩配置
            client_config = connection_config.get(player_uuid, {})
            enable_compression = client_config.get("enable_compression", False)
            
            if enable_compression:
                try:
                    # 压缩JSON数据
                    compressed_data = gzip.compress(message.encode('utf-8'))
                    # 消息格式：1字节标志位(0x01=压缩) + 压缩数据
                    payload = bytes([0x01]) + compressed_data
                except Exception as e:
                    print(f"Error compressing message for {player_uuid}: {e}")
                    # 压缩失败，使用未压缩的数据
                    payload = bytes([0x00]) + message.encode('utf-8')
            else:
                # 未压缩：1字节标志位(0x00=未压缩) + JSON数据
                payload = bytes([0x00]) + message.encode('utf-8')
            
            await ws.send_bytes(payload)
        except Exception as e:
            print(f"Error sending message to player {player_uuid}: {e}")
            disconnected.append(player_uuid)

    for player_uuid in disconnected:
        if player_uuid in connections:
            del connections[player_uuid]
        if player_uuid in connection_config:
            del connection_config[player_uuid]
        entities_to_remove = [eid for eid, edata in list(entities.items()) if edata.get("submitPlayerId") == player_uuid]
        for eid in entities_to_remove:
            del entities[eid]

    await broadcast_snapshot()


async def broadcast_snapshot():
    current_time = time.time()
    snapshot_data = {
        "server_time": current_time,
        "players": dict(players),
        "entities": dict(entities),
        "connections": list(connections.keys()),
        "connections_count": len(connections)
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
    submitPlayerId = None
    try:
        while True:
            message = await websocket.receive_text()
            try:
                data = json.loads(message)
            except json.JSONDecodeError as e:
                print(f"Error decoding JSON message: {e}")
                continue
            
            submitPlayerId = data.get("submitPlayerId")
            message_type = data.get("type")

            # 握手消息：客户端声明是否使用压缩
            if message_type == "handshake":
                if submitPlayerId:
                    enable_compression = data.get("enableCompression", False)
                    connections[submitPlayerId] = websocket
                    connection_config[submitPlayerId] = {
                        "enable_compression": enable_compression
                    }
                    compression_status = "启用" if enable_compression else "禁用"
                    print(f"Client {submitPlayerId} connected (compression: {compression_status})")
                    # 向客户端确认握手
                    await websocket.send_text(json.dumps({
                        "type": "handshake_ack",
                        "ready": True,
                        "compressionEnabled": enable_compression
                    }))
                    await broadcast_snapshot()
                continue

            if data.get("type") == "players_update":
                # 自动记录连接（兼容旧客户端）
                if submitPlayerId and submitPlayerId not in connections:
                    connections[submitPlayerId] = websocket
                    connection_config[submitPlayerId] = {
                        "enable_compression": data.get("enableCompression", False)
                    }
                    print(f"Client {submitPlayerId} connected (legacy)")
                # 支持批量更新多个玩家（推荐）
                current_time = time.time()
                for pid, player_data in data["players"].items():
                    try:
                        # 使用Pydantic模型验证和规范化数据
                        validated_data = PlayerData(**player_data)
                        players[pid] = {
                            "timestamp": current_time,
                            "submitPlayerId": submitPlayerId,
                            "data": validated_data.model_dump()
                        }
                    except Exception as e:
                        print(f"Error validating player data for {pid}: {e}")
                        continue
                await broadcast_positions()

            elif data.get("type") == "entities_update":
                # 自动记录连接（兼容旧客户端）
                if submitPlayerId and submitPlayerId not in connections:
                    connections[submitPlayerId] = websocket
                    connection_config[submitPlayerId] = {
                        "enable_compression": data.get("enableCompression", False)
                    }
                    print(f"Client {submitPlayerId} connected (legacy)")
                
                player_entities = data.get("entities", {})
                entities_to_remove = [eid for eid, edata in list(entities.items()) if edata.get("submitPlayerId") == submitPlayerId]
                for eid in entities_to_remove:
                    del entities[eid]

                current_time = time.time()
                for entity_id, entity_data in player_entities.items():
                    try:
                        # 使用Pydantic模型验证和规范化数据
                        validated_data = EntityData(**entity_data)
                        entities[entity_id] = {
                            "timestamp": current_time,
                            "submitPlayerId": submitPlayerId,
                            "data": validated_data.model_dump()
                        }
                    except Exception as e:
                        print(f"Error validating entity data for {entity_id}: {e}")
                        continue

                await broadcast_positions()
                

    except WebSocketDisconnect:
        pass
    except Exception as e:
        print(f"Error handling player message: {e}")
    finally:
        if submitPlayerId:
            if submitPlayerId in connections:
                del connections[submitPlayerId]
            if submitPlayerId in connection_config:
                del connection_config[submitPlayerId]
            players_to_remove = [pid for pid, pdata in list(players.items()) if pdata.get("submitPlayerId") == submitPlayerId]
            for pid in players_to_remove:
                del players[pid]
            entities_to_remove = [eid for eid, edata in list(entities.items()) if edata.get("submitPlayerId") == submitPlayerId]
            for eid in entities_to_remove:
                del entities[eid]
            print(f"Client {submitPlayerId} disconnected")
            await broadcast_positions()


@app.get("/health")
async def health_check():
    return JSONResponse({"status": "ok"})


@app.get("/snapshot")
async def snapshot():
    # 提供只读数据给后台页面
    current_time = time.time()
    # 返回副本，避免并发修改导致的迭代问题
    return JSONResponse({
        "server_time": current_time,
        "players": dict(players),
        "entities": dict(entities),
        "connections": list(connections.keys()),
        "connections_count": len(connections)
    })


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8765)