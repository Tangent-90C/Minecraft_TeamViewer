"""
客户端示例代码 - 如何连接到压缩的WebSocket服务
演示如何处理压缩和未压缩的数据
"""

import asyncio
import json
import gzip
import websockets

class CompressedWebSocketClient:
    """处理压缩WebSocket消息的客户端"""
    
    def __init__(self, server_url: str, player_id: str, enable_compression: bool = True):
        self.server_url = server_url
        self.player_id = player_id
        self.enable_compression = enable_compression
        self.websocket = None
    
    async def decompress_message(self, data: bytes) -> dict:
        """
        解压WebSocket消息
        
        消息格式：
        - 首字节为标志位：0x00=未压缩JSON，0x01=gzip压缩JSON
        - 后续数据为消息内容
        """
        if not data or len(data) < 1:
            raise ValueError("Invalid message format")
        
        compression_flag = data[0]
        payload = data[1:]
        
        if compression_flag == 0x01:
            # 压缩数据 - 使用gzip解压
            try:
                decompressed = gzip.decompress(payload)
                return json.loads(decompressed.decode('utf-8'))
            except Exception as e:
                print(f"Error decompressing message: {e}")
                raise
        elif compression_flag == 0x00:
            # 未压缩数据
            return json.loads(payload.decode('utf-8'))
        else:
            raise ValueError(f"Unknown compression flag: {compression_flag}")
    
    async def send_players_update(self, players: dict):
        """
        发送玩家数据更新
        
        Args:
            players: 玩家数据字典，格式为 {player_id: {x, y, z, ...}}
        """
        message = {
            "submitPlayerId": self.player_id,
            "type": "players_update",
            "players": players
        }
        await self.websocket.send(json.dumps(message))
    
    async def send_entities_update(self, entities: dict):
        """
        发送实体数据更新
        
        Args:
            entities: 实体数据字典，格式为 {entity_id: {x, y, z, ...}}
        """
        message = {
            "submitPlayerId": self.player_id,
            "type": "entities_update",
            "entities": entities
        }
        await self.websocket.send(json.dumps(message))
    
    async def connect(self):
        """连接到WebSocket服务器"""
        async with websockets.connect(self.server_url) as websocket:
            self.websocket = websocket
            print(f"Connected to {self.server_url}")
            try:
                # 发送握手消息，告知服务端是否使用压缩
                handshake_msg = {
                    "type": "handshake",
                    "submitPlayerId": self.player_id,
                    "enableCompression": self.enable_compression
                }
                await self.websocket.send(json.dumps(handshake_msg))
                
                # 等待握手确认
                ack = await self.websocket.recv()
                ack_data = json.loads(ack)
                if ack_data.get("type") == "handshake_ack":
                    print(f"Handshake successful, compression: {ack_data.get('compressionEnabled')}")
                
                await self.listen()
            except Exception as e:
                print(f"Error: {e}")
    
    async def listen(self):
        """监听来自服务器的消息"""
        async for raw_data in self.websocket:
            try:
                # 如果是字符串，直接作为JSON处理
                if isinstance(raw_data, str):
                    data = json.loads(raw_data)
                else:
                    # 如果是二进制数据，进行解压处理
                    data = await self.decompress_message(raw_data)
                
                # 处理接收到的数据
                if data.get("type") == "positions":
                    players = data.get("players", {})
                    entities = data.get("entities", {})
                    print(f"Received - Players: {len(players)}, Entities: {len(entities)}")
                    # 在这里处理玩家和实体位置数据
                    # self.on_positions_update(players, entities)
                    
            except Exception as e:
                print(f"Error processing message: {e}")


async def main():
    """示例使用"""
    # 配置
    SERVER_URL = "ws://localhost:8765/playeresp"
    PLAYER_ID = "player-001"  # 唯一玩家ID
    
    # 创建客户端，enable_compression 参数决定是否使用压缩
    client = CompressedWebSocketClient(SERVER_URL, PLAYER_ID, enable_compression=True)
    
    async def send_test_data():
        """定期发送测试数据"""
        await asyncio.sleep(1)  # 等待连接建立
        
        # 示例玩家数据
        test_players = {
            "player-001": {
                "x": 100.5,
                "y": 64.0,
                "z": 200.5,
                "vx": 0.1,
                "vy": 0.0,
                "vz": 0.0,
                "dimension": "minecraft:overworld",
                "playerName": "TestPlayer",
                "playerUUID": "12345678-1234-1234-1234-123456789012",
                "health": 20.0,
                "maxHealth": 20.0,
                "armor": 0.0
            }
        }
        
        while True:
            try:
                await client.send_players_update(test_players)
                await asyncio.sleep(0.5)  # 每0.5秒更新一次
            except Exception as e:
                print(f"Error sending update: {e}")
                break
    
    # 并发运行连接和发送任务
    await asyncio.gather(
        client.connect(),
        send_test_data()
    )


if __name__ == "__main__":
    print("=== 压缩WebSocket客户端示例 ===")
    print("此示例展示如何：")
    print("1. 连接到支持压缩的WebSocket服务器")
    print("2. 解压来自服务器的gzip压缩数据")
    print("3. 发送玩家/实体更新")
    print("\n配置说明（修改 player_esp_server.py 中的 ENABLE_COMPRESSION 变量）：")
    print("  - ENABLE_COMPRESSION = True  : 启用gzip压缩（推荐用于高频更新）")
    print("  - ENABLE_COMPRESSION = False : 禁用压缩，使用纯JSON")
    print()
    
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nDisconnected")
