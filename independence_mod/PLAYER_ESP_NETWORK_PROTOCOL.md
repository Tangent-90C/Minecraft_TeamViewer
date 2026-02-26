# MultiplePlayerESP 网络协议开发文档

本文档基于当前实现解读协议细节，覆盖服务端 `main.py` 与客户端 `PlayerESPNetworkManager.java` 的真实行为（不是理想设计）。

## 1. 协议目标与分层

该协议用于在多个 Minecraft 客户端之间同步三类数据：

- 玩家（players）
- 实体（entities）
- 路标（waypoints）

并同时服务两类消费者：

1. 游戏客户端：通过 `/playeresp` WebSocket 收发增量或全量数据
2. 管理端页面：通过 `/adminws` 接收服务端状态快照

此外保留了旧版兼容通道（`positions` 消息），确保不支持增量协议的客户端仍可工作。

---

## 2. 连接端点

### 2.1 `/playeresp`（游戏客户端）

- 连接后由客户端发送 `handshake`
- 服务端回 `handshake_ack`
- 之后根据协商能力走：
  - 增量模式：`snapshot_full` + `patch` + `digest`
  - 兼容模式：`positions`（全量）

### 2.2 `/adminws`（管理端）

- 仅推送状态，不参与游戏协议协商
- 每次状态变化后广播完整服务端快照（含原始内部节点结构）

### 2.3 HTTP 只读接口

- `GET /health`：健康检查
- `GET /snapshot`：完整快照 + 当前 revision + 三类 digest

---

## 3. 数据模型（业务字段）

服务端使用 Pydantic 校验。`extra="ignore"`，即允许客户端附带额外字段但会被丢弃。

### 3.1 PlayerData

必需字段：

- `x`, `y`, `z`（float）
- `dimension`（string）

常见可选字段：

- `vx`, `vy`, `vz`（默认 0）
- `playerName`, `playerUUID`
- `health`, `maxHealth`, `armor`
- `width`, `height`

### 3.2 EntityData

必需字段：

- `x`, `y`, `z`
- `dimension`

常见可选字段：

- `vx`, `vy`, `vz`
- `entityType`, `entityName`
- `width`, `height`

### 3.3 WaypointData

必需字段：

- `x`, `y`, `z`
- `dimension`
- `name`

常见可选字段：

- `symbol`（默认 `"W"`）
- `color`
- `ownerId`, `ownerName`, `createdAt`
- `ttlSeconds`（路标超时秒数，范围 5~86400）
- `waypointKind`（`quick` / `manual`）
- `replaceOldQuick`（仅 `quick` 有意义，表示是否替换该来源旧 quick）
- `targetType`（`block` / `entity`）
- `targetEntityId`, `targetEntityType`, `targetEntityName`

---

## 4. 服务端内部状态结构

服务端内部实际保存的是“节点包装结构”，而不是纯业务对象：

```json
{
  "<id>": {
    "timestamp": 1700000000.123,
    "submitPlayerId": "<连接玩家UUID>",
    "data": { ...业务字段... }
  }
}
```

其中：

- `timestamp`：最近更新时间（秒）
- `submitPlayerId`：是谁提交了该数据
- `data`：校验后的业务字段

注意：

- 增量全量快照（`snapshot_full`）发送的是压缩后 `data` 视图
- 兼容 `positions` 与管理端 `/adminws` 发送的是原始节点结构
- 客户端通过 `extractDataNode` 兼容两种形状

---

## 5. 协商与连接生命周期

### 5.1 客户端发起握手

客户端连接成功后发送：

```json
{
  "type": "handshake",
  "protocolVersion": 2,
  "supportsDelta": true,
  "submitPlayerId": "<可选，玩家UUID>"
}
```

### 5.2 服务端握手确认

服务端回：

```json
{
  "type": "handshake_ack",
  "ready": true,
  "protocolVersion": 2,
  "deltaEnabled": true,
  "digestIntervalSec": 10,
  "rev": <当前revision>
}
```

`deltaEnabled` 计算规则：

- `protocolVersion >= 2` 且客户端 `supportsDelta=true`

### 5.3 握手后首轮同步

服务端会触发一次广播：

- 对增量客户端：`force_full_to_delta=true`，即立刻下发 `snapshot_full`
- 对非增量客户端：按老协议路径接收 `positions`
- 管理端同时收到服务端快照

---

## 6. 消息类型总览

### 6.1 客户端 -> 服务端

1. `handshake`
2. `players_update`（旧全量）
3. `players_patch`（增量）
4. `entities_update`（旧全量）
5. `entities_patch`（增量）
6. `waypoints_update`
7. `waypoints_delete`
8. `waypoints_entity_death_cancel`
9. `resync_req`
10. `tab_players_update`（仅上报给服务端，用于同服判定与管理端分析，不向游戏客户端广播）

### 6.2 服务端 -> 客户端

1. `handshake_ack`
2. `snapshot_full`
3. `patch`
4. `digest`
5. `positions`（兼容模式）

### 6.3 服务端 -> 管理端

- 无 `type` 字段的状态快照 JSON（包含 players/entities/waypoints/connections/revision）
- 快照新增 `tabState`：
  - `enabled`：同服隔离开关状态
  - `reports`：各上报源的 tab 玩家列表
  - `groups`：服务端基于 tab 交集推导出的“同服分组”

---

## 6.4 tab 玩家列表上报

客户端可周期上报当前 TAB 列表：

```json
{
  "type": "tab_players_update",
  "submitPlayerId": "<本地玩家UUID>",
  "ackRev": 123,
  "tabPlayers": [
    {"id": "<玩家UUID>", "name": "PlayerA", "displayName": "PlayerA", "prefixedName": "[xxx]PlayerA"},
    {"id": "<玩家UUID>", "name": "PlayerB", "displayName": "PlayerB", "prefixedName": "[yyy]PlayerB"}
  ]
}
```

用途：

1. 服务端在启用同服隔离时，基于 tab 列表交集推导“同服组”，避免跨服互相广播。
2. 管理端（含油猴脚本）可读取 `tabState.reports` / `tabState.groups` 做战术识别（如按名字标签自动敌我分类）。

说明：
- `name` 维持纯玩家名（用于同服分组 identityKey）。
- `prefixedName` 用于携带服务器自定义前缀（用于前端自动敌我识别）。

---

## 7. 增量同步核心机制

### 7.1 版本号（revision / rev）

- 服务端维护全局 `revision`（初始 0）
- 每次有真实变更（`upsert` 或 `delete` 非空）时 `revision += 1`
- 变更消息中的 `rev` 是该次变更对应版本

### 7.2 Patch 结构

固定三域：

```json
{
  "type": "patch",
  "rev": 123,
  "players": {"upsert": {}, "delete": []},
  "entities": {"upsert": {}, "delete": []},
  "waypoints": {"upsert": {}, "delete": []}
}
```

### 7.3 字段级差量

服务端对玩家/实体使用字段级差量：

- 首次出现：整条对象作为 `upsert[id]`
- 已存在：仅变化字段进入 `upsert[id]`

Waypoints 当前策略：

- `waypoints_update` 写入时通常直接携带完整对象（非字段级差量）
- `waypoints_update` 内可携带业务扩展字段（如 `ttlSeconds`、`waypointKind`、`targetEntity*`）

### 7.4 客户端本地合并

客户端对 `players/entities/waypoints` 都是“本地缓存 + patch字段覆盖”：

1. 先处理 `delete`
2. 再处理 `upsert`（与已有 map 合并）

---

## 8. 一致性校验（Digest）与重同步

### 8.1 Digest 的作用

用于检测“客户端缓存”和“服务端状态”是否漂移。

服务端周期性（受广播节流）向增量客户端下发：

```json
{
  "type": "digest",
  "rev": 123,
  "hashes": {
    "players": "16hex",
    "entities": "16hex",
    "waypoints": "16hex"
  }
}
```

### 8.2 Digest 计算规则（双方需一致）

核心流程：

1. 对 ID 排序
2. 每条拼接：`json(id) + ":" + canonicalValue(data)`
3. 按换行拼接后做 SHA-1
4. 取前 16 个十六进制字符（即 8 字节）

数值 canonical 规则：

- 非有限值（NaN/Inf）=> `null`
- 浮点保留 6 位小数后去尾零
- `-0` 归一为 `0`

### 8.3 触发重同步

客户端收到 `digest` 后：

- 若任意域 hash 不同，触发 `resync_req`
- 限流：`RESYNC_COOLDOWN_MS = 3000`

请求示例：

```json
{
  "type": "resync_req",
  "reason": "digest_mismatch",
  "ackRev": 123,
  "submitPlayerId": "<可选>"
}
```

服务端行为：

- 向请求方单播 `snapshot_full`

说明：

- 当前服务端未使用 `reason` / `ackRev` 参与逻辑，仅按 `submitPlayerId` 定向返回

---

## 9. 兼容模式（Legacy）

若客户端不支持 delta（或握手前 legacy 路径）：

- 服务端通过 `positions` 广播全量状态
- 客户端 `applyLegacyPositions` 直接覆盖式应用

`positions` 结构：

```json
{
  "type": "positions",
  "players": {"id": {"timestamp":..., "submitPlayerId":..., "data": {...}}},
  "entities": {"id": {"timestamp":..., "submitPlayerId":..., "data": {...}}},
  "waypoints": {"id": {"timestamp":..., "submitPlayerId":..., "data": {...}}}
}
```

---

## 10. 超时与清理语义

### 10.1 超时阈值

- `PLAYER_TIMEOUT = 5s`
- `ENTITY_TIMEOUT = 5s`

玩家与实体均按统一基础超时处理，不再根据提交者是否在线额外放宽。

### 10.2 哪些会超时

- 玩家、实体：会被 `cleanup_timeouts()` 删除
- 路标：会按 `effective_waypoint_timeout()` 清理
  - 若带 `ttlSeconds`：使用该值（并夹紧到 5~86400）
  - 若未带 `ttlSeconds`：回退到 `WAYPOINT_TIMEOUT = 120s`

### 10.3 断连清理

某连接断开时：

- 清掉该连接自己的 players/entities/waypoints（按 `submitPlayerId` 匹配）
- 广播删除 patch

---

## 11. 三类数据的关键差异（易错点）

### 11.1 `players_update` vs `entities_update`

- `entities_update`：会先删除该提交者旧实体，再写入新集合（“替换语义”）
- `players_update`：不会先删旧玩家，只做逐条 upsert（更偏“累加语义”）

这意味着：

- 玩家旧条目可能依赖超时或断连才清掉
- 实体全量更新会立刻反映“本轮不存在即删除”

### 11.2 Waypoint 语义

- `waypoints_update`：upsert
- `waypoints_delete`：按 ID 显式删除
- 支持 TTL 自动清理（见 10.2）
- 当 `waypointKind == "quick"` 且 `replaceOldQuick == true` 时，服务端会先删除同一来源下其他 quick 路标，再写入当前路标
- `waypoints_entity_death_cancel`：按 `targetEntityIds` 在全来源删除满足 `targetType=="entity"` 且 `targetEntityId` 命中的路标（当前默认所有玩家提交都被信任）

---

## 12. 字段与容错约束

### 12.1 服务端校验失败

- 单条数据校验失败只记录日志，不会中断整包处理
- 有效条目仍会继续广播

### 12.2 非法消息容错

- JSON 解析失败：忽略该消息
- 未识别 `type`：忽略
- WebSocket 发送失败：将连接标记为断开并清理

### 12.3 客户端容错

- 单条 patch 解析失败仅影响该条
- UUID 解析失败会跳过该项
- 数值提取失败返回 `null`，导致该对象可能被忽略

---

## 13. 时序示例

### 13.1 标准增量链路

1. 客户端连接 `/playeresp`
2. 客户端发 `handshake`
3. 服务端回 `handshake_ack`
4. 服务端发 `snapshot_full`
5. 客户端周期上报 `players_patch/entities_patch`
6. 服务端广播 `patch` 到所有 delta 客户端
7. 服务端按节流间隔插入 `digest`
8. 若 hash 不一致，客户端发 `resync_req`
9. 服务端单播 `snapshot_full`

### 13.2 Legacy 链路

1. 客户端不支持 delta（或未协商成功）
2. 服务端在有变更时推送 `positions`
3. 客户端整包覆盖本地视图

---

## 14. 协议字段参考（速查）

### 14.1 公共字段

- `type`: 消息类型
- `submitPlayerId`: 提交方玩家 UUID（字符串）
- `rev`: 服务端修订号
- `ackRev`: 客户端已知修订号（当前主要用于上报，不参与服务端判定）

### 14.2 Patch 子结构

- `upsert`: `Map<id, partialOrFullObject>`
- `delete`: `string[]`

---

## 15. 实现建议与注意事项

1. **跨端一致性优先**：任何 canonical 规则改动都必须同步修改 Python 与 Java 两侧。
2. **新增字段策略**：先在服务端模型加字段（允许默认值），再逐步放开客户端发送，避免校验拒收。
3. **不要依赖 `ackRev` 做强一致**：当前服务端未基于 `ackRev` 做差异回放或冲突处理。
4. **重连后必须重新握手**：客户端已实现，若自定义实现需保持一致。
5. **注意 players/entities 语义差异**：如需统一替换语义，需单独设计并同步改造两端。

---

## 16. 与源码对应关系

- 服务端协议实现：`main.py`
  - 握手与路由：`/playeresp`
  - 增量广播：`broadcast_updates`
  - Digest：`build_digests`, `state_digest`
  - 超时清理：`cleanup_timeouts`
- 客户端协议实现：`src/client/java/person/professor_chen/teamviewer/multipleplayeresp/PlayerESPNetworkManager.java`
  - 握手：`sendHandshake`, `handleHandshakeAck`
  - 消息分发：`processCompleteMessage`
  - 增量应用：`applyPatch`, `applySnapshot`
  - 校验与重同步：`handleDigest`, `sendResyncRequest`

如果后续你要做协议演进（例如 v3：引入 seq/ack、压缩批次、waypoint patch 化），建议在本文件末尾追加“版本演进记录”章节，保证线上互通行为可追溯。
