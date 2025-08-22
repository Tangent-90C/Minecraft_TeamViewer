import asyncio
import websockets
import json
import time
from collections import defaultdict

# 存储玩家数据和连接
players = {}
connections = {}

# 存储实体数据
entities = {}

# 存储玩家数据的时间限制（秒）
PLAYER_TIMEOUT = 5
ENTITY_TIMEOUT = 5

async def handle_player(websocket):
    player_id = None
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
            except json.JSONDecodeError as e:
                print(f"Error decoding JSON message: {e}")
                continue

            print(data)
            
            if data["type"] == "register":
                player_id = data["id"]
                connections[player_id] = websocket
                print(f"Player {player_id} registered")
                
            elif data["type"] == "update" and player_id:
                # 更新玩家位置数据
                players[player_id] = {
                    "x": data["x"],
                    "y": data["y"], 
                    "z": data["z"],
                    "dimension": data["dimension"],
                    "timestamp": time.time()
                }
                # 广播给所有其他玩家
                await broadcast_positions()
                
            elif data["type"] == "entities_update" and player_id:
                # 更新实体位置数据
                player_entities = data["entities"]
                # 清除该玩家之前的所有实体
                entities_to_remove = [eid for eid, edata in entities.items() if edata.get("playerId") == player_id]
                for eid in entities_to_remove:
                    del entities[eid]
                
                # 添加新的实体数据
                for entity_id, entity_data in player_entities.items():
                    entity_data["playerId"] = player_id
                    entity_data["timestamp"] = time.time()
                    entities[entity_id] = entity_data
                    
                # 广播给所有其他玩家
                await broadcast_positions()
                
    except websockets.exceptions.ConnectionClosed:
        pass
    except Exception as e:
        print(f"Error handling player message: {e}")
    finally:
        # 清理断开的连接
        if player_id:
            if player_id in connections:
                del connections[player_id]
            if player_id in players:
                del players[player_id]
            # 清理该玩家的实体
            entities_to_remove = [eid for eid, edata in entities.items() if edata.get("playerId") == player_id]
            for eid in entities_to_remove:
                del entities[eid]
            print(f"Player {player_id} disconnected")
            # 通知其他玩家该玩家已离线
            await broadcast_positions()

async def broadcast_positions():
    # 清理过期数据
    current_time = time.time()
    expired_players = [
        pid for pid, pdata in players.items() 
        if current_time - pdata["timestamp"] > PLAYER_TIMEOUT
    ]
    for pid in expired_players:
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

    # 使用dumps的参数确保生成完整的JSON
    try:
        message = json.dumps(message_data, separators=(',', ':'))
    except Exception as e:
        print(f"Error serializing message data: {e}")
        return

    print(message)
    print('===========---==================')
    
    # 发送给所有连接的客户端
    disconnected = []
    for player_id, websocket in connections.items():
        try:
            # 确保消息完整发送
            await websocket.send(message)
        except websockets.exceptions.ConnectionClosed:
            disconnected.append(player_id)
        except Exception as e:
            print(f"Error sending message to player {player_id}: {e}")
            disconnected.append(player_id)
    
    # 清理断开的连接
    for player_id in disconnected:
        if player_id in connections:
            del connections[player_id]
        if player_id in players:
            del players[player_id]
        # 清理该玩家的实体
        entities_to_remove = [eid for eid, edata in entities.items() if edata.get("playerId") == player_id]
        for eid in entities_to_remove:
            del entities[eid]

async def main():
    print("Starting Player ESP Server on port 8765...")
    server = await websockets.serve(handle_player, "localhost", 8765)
    await server.wait_closed()

if __name__ == "__main__":
    asyncio.run(main())