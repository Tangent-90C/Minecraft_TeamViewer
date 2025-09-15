import json
import time
from typing import Dict

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles

# 存储玩家数据和连接
players: Dict[str, dict] = {}
connections: Dict[str, WebSocket] = {}

# 存储实体数据
entities: Dict[str, dict] = {}

# 存储玩家数据的时间限制（秒）
PLAYER_TIMEOUT = 5
ENTITY_TIMEOUT = 5

app = FastAPI()

# 挂载静态页面目录到 /admin
app.mount("/admin", StaticFiles(directory="Server/static", html=True), name="admin")


async def broadcast_positions():
    # 清理过期数据
    current_time = time.time()
    expired_players = [
        pid for pid, pdata in players.items()
        if current_time - pdata["timestamp"] > PLAYER_TIMEOUT
    ]
    for pid in expired_players:
        if pid in players:
            del players[pid]
        if pid in connections:
            del connections[pid]

    # 清理过期实体数据
    expired_entities = [
        eid for eid, edata in entities.items()
        if current_time - edata["timestamp"] > ENTITY_TIMEOUT
    ]
    for eid in expired_entities:
        del entities[eid]

    # 准备要发送的数据
    message_data = {
        "type": "positions",
        "players": players,
        "entities": entities
    }

    try:
        message = json.dumps(message_data, separators=(",", ":"))
    except Exception as e:
        print(f"Error serializing message data: {e}")
        return

    # print(message)
    # print('===========---==================')

    disconnected = []
    for player_uuid, ws in connections.items():
        try:
            await ws.send_text(message)
        except Exception as e:
            print(f"Error sending message to player {player_uuid}: {e}")
            disconnected.append(player_uuid)

    for player_uuid in disconnected:
        if player_uuid in connections:
            del connections[player_uuid]
        if player_uuid in players:
            del players[player_uuid]
        entities_to_remove = [eid for eid, edata in entities.items() if edata.get("playerId") == player_uuid]
        for eid in entities_to_remove:
            del entities[eid]


@app.websocket("/")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    player_uuid = None
    try:
        while True:
            message = await websocket.receive_text()
            try:
                data = json.loads(message)
            except json.JSONDecodeError as e:
                print(f"Error decoding JSON message: {e}")
                continue

            print(data)

            if data.get("type") == "register":
                player_uuid = data.get("playerUUID")
                if player_uuid is not None:
                    connections[player_uuid] = websocket
                    print(f"Player {player_uuid} registered")

            elif data.get("type") == "players_update" and player_uuid:
                players[player_uuid] = {
                    "x": data["x"],
                    "y": data["y"],
                    "z": data["z"],
                    "dimension": data["dimension"],
                    "timestamp": time.time(),
                    "playerName" : data.get("name"),
                    "playerUUID": player_uuid,
                    "health": data.get("health", 0),
                    "maxHealth": data.get("maxHealth", 0),
                    "armor": data.get("armor", 0),
                    "width": data.get("width", 0.6),     # 默认玩家宽度
                    "height": data.get("height", 1.8)    # 默认玩家高度
                }
                await broadcast_positions()

            elif data.get("type") == "entities_update" and player_uuid:
                player_entities = data.get("entities", {})
                entities_to_remove = [eid for eid, edata in entities.items() if edata.get("submitPlayerId") == player_uuid]
                for eid in entities_to_remove:
                    del entities[eid]

                for entity_id, entity_data in player_entities.items():
                    entity_data["submitPlayerId"] = player_uuid
                    entity_data["timestamp"] = time.time()
                    entities[entity_id] = entity_data

                await broadcast_positions()

    except WebSocketDisconnect:
        pass
    except Exception as e:
        print(f"Error handling player message: {e}")
    finally:
        if player_uuid:
            if player_uuid in connections:
                del connections[player_uuid]
            if player_uuid in players:
                del players[player_uuid]
            entities_to_remove = [eid for eid, edata in entities.items() if edata.get("playerId") == player_uuid]
            for eid in entities_to_remove:
                del entities[eid]
            print(f"Player {player_uuid} disconnected")
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
        "players": players,
        "entities": entities,
        "connections": list(connections.keys()),
        "connections_count": len(connections)
    })


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8765)