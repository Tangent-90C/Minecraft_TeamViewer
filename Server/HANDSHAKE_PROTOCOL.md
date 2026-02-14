# WebSocket 握手协议 - 客户端配置压缩

## 概述

客户端现在可以在握手时决定是否启用数据压缩，而不是由服务端全局配置。这提供了更灵活的控制方式。

## 握手流程

```
客户端                                    服务端
  |                                        |
  |-------- 发送握手消息 +------>         |
  |  (type: "handshake"                  |
  |   enableCompression: true/false)     |
  |                                        |
  |<------- 握手确认 -------+ 接收握手    |
  |  (type: "handshake_ack"   并记录配置 |
  |   compressionEnabled: bool)           |
  |                                        |
  |<====== 接收位置数据 ======           |
  |  (根据握手时的配置                  |
  |   自动压缩或不压缩)                  |
  |                                        |
```

## 握手消息格式

### 客户端->服务端：握手请求

```json
{
  "type": "handshake",
  "submitPlayerId": "player-id",
  "enableCompression": true/false
}
```

**参数说明**:
- `type`: "handshake" - 固定值，表示握手消息
- `submitPlayerId`: 字符串，唯一的客户端标识符
- `enableCompression`: 布尔值
  - `true`: 启用 gzip 压缩传输
  - `false`: 禁用压缩，传输原始 JSON

### 服务端->客户端：握手确认

```json
{
  "type": "handshake_ack",
  "ready": true,
  "compressionEnabled": true/false
}
```

**参数说明**:
- `type`: "handshake_ack" - 握手确认消息
- `ready`: 布尔值，连接是否就绪
- `compressionEnabled`: 布尔值，确认服务端将使用的压缩设置

## 数据传输格式

握手完成后，服务端将根据客户端的配置发送数据。

### 如果启用压缩 (enableCompression: true)

```
[0x01] [gzip压缩数据]
```

- 首字节: 0x01 (压缩标志位)
- 后续字节: gzip 压缩的 JSON 数据

### 如果禁用压缩 (enableCompression: false)

```
[0x00] [原始JSON字符串的字节]
```

- 首字节: 0x00 (未压缩标志位)
- 后续字节: 原始 JSON 数据

## 实现示例

### Python 客户端

```python
import asyncio
import json
from client_example import CompressedWebSocketClient

async def main():
    # 创建客户端，enable_compression=True 表示启用压缩
    client = CompressedWebSocketClient(
        'ws://localhost:8765/playeresp',
        'player-001',
        enable_compression=True  # 客户端决定是否启用压缩
    )
    
    # 连接时自动发送握手消息
    await client.connect()

asyncio.run(main())
```

握手执行流程：
1. 建立 WebSocket 连接
2. 自动发送握手消息，告诉服务端客户端的压缩偏好
3. 等待握手确认
4. 之后接收的数据将根据握手时的配置自动处理

### JavaScript 客户端

```javascript
// 创建客户端，第3个参数控制是否启用压缩
const client = new CompressedWebSocketClient(
    'ws://localhost:8765/playeresp',
    'player-' + Date.now(),
    true  // 启用压缩，改为 false 以禁用
);

// 连接时自动发送握手消息
client.onPositionsUpdate = (players, entities) => {
    console.log('Data received:', players, entities);
};

client.connect();
```

握手执行流程：
1. WebSocket 连接建立
2. onopen 回调中自动发送握手消息
3. 接收握手确认 (type: "handshake_ack")
4. 之后接收位置数据，客户端自动根据压缩标志位处理

### Java/C# 模组

```java
// 伪代码示例
class MinecraftModClient {
    void connect() {
        // 建立 WebSocket 连接
        WebSocket ws = new WebSocket("ws://localhost:8765/playeresp");
        
        ws.onOpen(() -> {
            // 发送握手消息
            JSONObject handshake = new JSONObject();
            handshake.put("type", "handshake");
            handshake.put("submitPlayerId", "my-player");
            handshake.put("enableCompression", true);  // 客户端决定
            
            ws.send(handshake.toString());
        });
        
        ws.onMessage((data) -> {
            byte compressionFlag = data[0];
            byte[] payload = Arrays.copyOfRange(data, 1, data.length);
            
            if (compressionFlag == 0x01) {
                // 解压 gzip
                payload = decompressGzip(payload);
            }
            
            // 解析 JSON
            processJSON(new String(payload));
        });
    }
}
```

## 向后兼容性

### 旧客户端（不发送握手消息）

如果客户端直接发送 `players_update` 或 `entities_update` 消息（不先发送握手），服务端会：

1. **自动检测客户端连接**
   - 如果消息中包含 `enableCompression` 字段，使用该值
   - 否则默认不压缩 (`enableCompression: false`)

2. **记录客户端配置**
   - 为该客户端创建配置记录
   - 后续发送的数据根据该配置处理

### 升级时的注意事项

- ✅ 新服务端支持旧客户端（旧客户端不发送握手）
- ✅ 旧服务端不支持新协议（会忽略握手消息）
- 📝 建议新客户端始终发送握手消息

## 配置建议

### 启用压缩的场景

- 网络连接较慢（WAN、移动网络）
- 客户端数据更新频率高 (>10/s)
- 数据量较大 (>100KB/更新)
- 客户端硬件性能尚可（CPU 占用可接受）

### 禁用压缩的场景

- 本地 LAN 网络，带宽充足
- 数据更新频率低 (<5/s)
- 数据量很小 (<10KB/更新)
- 客户端硬件性能有限（如嵌入式设备）
- 优先考虑低延迟而非低带宽

## 性能对比

### 启用压缩 (enableCompression: true)

```
优点：
  ✓ 带宽减少 70-85%
  ✓ 网络传输时间减少
  ✓ 总实际延迟通常降低

缺点：
  ✗ CPU 占用增加 (1-5ms 压缩时间)
```

### 禁用压缩 (enableCompression: false)

```
优点：
  ✓ CPU 占用更低
  ✓ 压缩/解压延迟为 0
  ✓ 处理逻辑简单

缺点：
  ✗ 带宽占用大 (原始大小)
  ✗ 网络传输时间长
```

## 故障排查

### 问题：客户端无法连接

**检查**:
1. WebSocket 地址是否正确？
2. 服务端是否在运行？
3. 防火墙是否开放 8765 端口？

### 问题：握手后未收到数据

**检查**:
1. 是否等待了握手确认？(type: "handshake_ack")
2. 服务端是否没有数据？(检查 /snapshot 端点)
3. 数据传输格式是否正确？
   - 启用压缩时：首字节应该是 0x01
   - 禁用压缩时：首字节应该是 0x00

### 问题：解压失败

**症状**:
```
Error decompressing: incorrect data check
```

**原因**:
1. 数据实际上未压缩，但标志位是 0x01
2. 网络传输过程中数据损坏
3. 客户端/服务端配置不一致

**解决**:
1. 检查握手确认中的 `compressionEnabled` 字段
2. 放弃连接，重新连接重试
3. 临时禁用压缩以排除问题

## 消息流示例

### 场景：启用压缩

```
时间    方向  消息
-----  ----  ─────────────────────────────────────────
T0     C->S  {type: "handshake", submitPlayerId: "p1", enableCompression: true}
T1     S->C  {type: "handshake_ack", ready: true, compressionEnabled: true}
T2     C->S  {type: "players_update", submitPlayerId: "p1", players: {...}}
T3     S->C  0x01 + [gzip compressed JSON] ← 位置数据（压缩）
T4     S->C  0x01 + [gzip compressed JSON] ← 位置数据（压缩）
...
```

### 场景：禁用压缩

```
时间    方向  消息
-----  ----  ─────────────────────────────────────────
T0     C->S  {type: "handshake", submitPlayerId: "p1", enableCompression: false}
T1     S->C  {type: "handshake_ack", ready: true, compressionEnabled: false}
T2     C->S  {type: "players_update", submitPlayerId: "p1", players: {...}}
T3     S->C  0x00 + [JSON string] ← 位置数据（未压缩）
T4     S->C  0x00 + [JSON string] ← 位置数据（未压缩）
...
```

## 总结

| 方面 | 握手前 | 握手后 |
|------|--------|--------|
| **控制权** | 服务端全局配置 | 客户端个别配置 |
| **灵活性** | 低（所有客户端相同） | 高（每个客户端自主选择） |
| **兼容性** | 旧客户端需修改 | 新旧客户端可混用 |
| **数据格式** | 固定 | 根据握手动态设置 |

## 相关文件

- [player_esp_server.py](player_esp_server.py) - 服务端实现
- [client_example.py](client_example.py) - Python 客户端示例
- [client_example.js](client_example.js) - JavaScript 客户端示例
- [README.md](README.md) - 完整技术文档
