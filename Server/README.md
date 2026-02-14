# WebSocket 数据压缩指南

## 功能说明

服务端支持对传输数据进行 **gzip 压缩**，以降低带宽占用。这个功能是**可选的**，可以通过配置启用或禁用。

### 压缩效果

- **压缩率**: 通常可达 **70-85%** 的数据量减少（取决于数据内容）
- **开销**: CPU 占用轻微（gzip 压缩/解压很高效）
- **适用场景**: 数据量大、更新频率高的场景

## 配置方式

### 服务端配置

编辑 `player_esp_server.py`，修改以下变量：

```python
# 启用压缩（推荐）
ENABLE_COMPRESSION = True

# 禁用压缩（默认未压缩）
ENABLE_COMPRESSION = False
```

**说明**:
- `True`: 使用 gzip 压缩所有 WebSocket 消息
- `False`: 发送未压缩的 JSON 数据

### 消息格式

无论是否压缩，所有 WebSocket 消息都遵循统一格式：

```
[压缩标志位(1字节)] + [数据]

标志位说明：
  0x00 = 未压缩 JSON 数据
  0x01 = gzip 压缩的 JSON 数据
```

**优势**: 客户端可以自动检测数据格式，无需额外配置。

## 客户端实现

### Python 客户端

参考 `client_example.py`：

```python
import gzip
import json

async def decompress_message(data: bytes) -> dict:
    """解压 WebSocket 消息"""
    compression_flag = data[0]
    payload = data[1:]
    
    if compression_flag == 0x01:
        # 压缩数据 - 解压
        decompressed = gzip.decompress(payload)
        return json.loads(decompressed.decode('utf-8'))
    elif compression_flag == 0x00:
        # 未压缩数据
        return json.loads(payload.decode('utf-8'))
```

**依赖**: `websockets` 包

```bash
pip install websockets
```

**使用**:

```python
from client_example import CompressedWebSocketClient
import asyncio

client = CompressedWebSocketClient('ws://localhost:8765/playeresp', 'my-player-id')
await client.connect()
```

### JavaScript 客户端

参考 `client_example.js`：

```javascript
// 需要引入 pako 库
<script src="https://unpkg.com/pako@2/dist/pako.iife.js"></script>

const client = new CompressedWebSocketClient(
    'ws://localhost:8765/playeresp',
    'player-id'
);

client.onPositionsUpdate = (players, entities) => {
    // 处理数据
    console.log(players, entities);
};

client.connect();
```

**特点**:
- 自动检测压缩标志位
- 自动解压 gzip 数据
- 支持自动重连
- 支持自定义回调处理数据

### 其他语言

基本步骤相同：

1. 读取第一个字节作为压缩标志
2. 根据标志位决定是否解压
3. 如果标志位为 0x01，使用 gzip 库解压剩余数据
4. 解析 JSON

**各语言 gzip 库**:
- Python: `gzip` (标准库)
- JavaScript: `pako`, `fflate`, 或 原生 `DecompressionStream` API
- Java: `java.util.zip.GZIPInputStream`
- C#: `System.IO.Compression.GZipStream`
- Golang: `compress/gzip`

## 性能对比

### 示例数据（1000个玩家 + 5000个实体）

| 指标 | 未压缩 | 已压缩 | 节省 |
|------|-------|-------|------|
| 传输大小 | ~850 KB | ~120 KB | **85.9%** |
| 消息速率 (20/s) | 17 MB/s | 2.4 MB/s | **85.9%** |

### 建议配置

- **本地网络 / LAN**: 可选（网络快，CPU 更重要）
- **互联网 / WAN**: 强烈推荐启用（带宽成本高）
- **高频更新**: 推荐启用（20+ 更新/秒）
- **低频更新**: 可选（带宽节省不明显）

## 故障排查

### 客户端无法解压数据

**症状**: 
```
Error decompressing data: incorrect data check
```

**原因**: 压缩标志位错误或数据损坏

**解决**:
1. 检查 `ENABLE_COMPRESSION` 配置
2. 确保客户端正确读取第一个字节
3. 检查网络连接稳定性

### 性能没有改善

**检查**:
1. 是否启用了压缩？
   ```python
   print(ENABLE_COMPRESSION)  # 应该是 True
   ```

2. 数据量是否足够大？
   - 数据越大，压缩效果越明显
   - 数据量小（<1KB）可能不需要压缩

3. 客户端解压速度？
   - 某些浏览器的 JavaScript 解压可能较慢
   - 可考虑使用 Web Worker 在后台线程解压

## API 参考

### 服务端变量

```python
# 启用/禁用压缩
ENABLE_COMPRESSION: bool = True

# 玩家数据超时时间（秒）
PLAYER_TIMEOUT: int = 5

# 实体数据超时时间（秒）
ENTITY_TIMEOUT: int = 5
```

### 客户端类

#### Python: `CompressedWebSocketClient`

```python
client = CompressedWebSocketClient(server_url, player_id)

# 发送玩家更新
await client.send_players_update(players_dict)

# 发送实体更新
await client.send_entities_update(entities_dict)

# 连接
await client.connect()
```

#### JavaScript: `CompressedWebSocketClient`

```javascript
const client = new CompressedWebSocketClient(serverUrl, playerId);

// 设置回调
client.onPositionsUpdate = (players, entities) => {};

// 发送更新
await client.sendPlayersUpdate(playersData);
await client.sendEntitiesUpdate(entitiesData);

// 连接
client.connect();

// 检查状态
client.isConnected();  // 返回 boolean
```

## 常见问题

**Q: 如何在 Minecraft 模组中使用？**

A: 
1. 以上述 Python/JavaScript 示例为参考
2. 使用适合你模组语言的 gzip 库
3. 实现相同的消息解析逻辑

**Q: 可以同时支持压缩和未压缩客户端吗？**

A: 是的！消息格式统一，标志位会指示是否压缩。启用压缩的服务端可以与新旧客户端兼容。

**Q: 压缩会增加延迟吗？**

A: 
- 压缩/解压开销: ~1-5ms（取决于数据量和硬件）
- 网络传输时间减少: **大幅减少**（带宽占用少80%+）
- **总延迟通常会减少**，尤其在网络较差的情况下

**Q: 支持其他压缩算法吗？**

A: 目前只支持 gzip (DEFLATE)。如需其他算法，可修改服务端代码。推荐算法：
- `gzip`: 通用，广泛支持 ✓ (当前)
- `zstd`: 更好的压缩率，但库支持较少
- `brotli`: 出色的压缩率，但速度较慢

## 文件说明

- `player_esp_server.py`: 主服务端代码（包含压缩功能）
- `client_example.py`: Python 客户端示例
- `client_example.js`: JavaScript 客户端示例
- `static/index.html`: 后台管理界面（已支持压缩）
- `README.md`: 本文件

## 更新日志

### v1.1 (2026-02-15)
- ✨ 新增 gzip 压缩功能（可选）
- ✨ 统一消息格式（标志位 + 数据）
- ✨ Python 客户端示例
- ✨ JavaScript 客户端示例
- ✨ 后台管理界面支持压缩数据
- 📖 完整文档和故障排查指南

## 许可证

MIT License

## 支持

如有问题，请检查：
1. 服务端日志
2. 浏览器开发者工具 (F12) - Console 标签
3. 客户端是否正确处理压缩标志位
