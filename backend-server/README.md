# backend-server

Minecraft TeamViewer 的后端聚合服务，基于 FastAPI + WebSocket。

## 功能

- 接收客户端上报的玩家/实体/路标数据
- 按 `roomCode` 分房广播
- 提供 `adminws` 管理通道（状态快照、指令）
- 支持增量同步（`snapshot_full` / `patch` / `digest`）

## 环境

- Python `>=3.12`
- 推荐使用 `uv`

## 启动

```bash
cd backend-server/src
uv run main.py
```

默认监听：`0.0.0.0:8765`

## 关键端点

- 玩家 WS：`/playeresp`
- 管理 WS：`/adminws`
- 健康检查：`/health`
- 快照调试：`/snapshot`

## 运行配置

配置文件：`src/server/server_state_config.toml`

可配置项包括：

- 对象超时（玩家 / 实体 / 路标）
- digest 间隔
- 广播频率与拥塞降级阈值
- 同服过滤开关（Tab 列表归并）

## 协议版本

当前服务端协议常量位于 `src/main.py`：

- `NETWORK_PROTOCOL_VERSION = 0.4.0`
- `SERVER_MIN_COMPATIBLE_PROTOCOL_VERSION = 0.4.0`

详细字段与报文结构见仓库根目录文档：`docs/PLAYER_ESP_NETWORK_PROTOCOL.md`
