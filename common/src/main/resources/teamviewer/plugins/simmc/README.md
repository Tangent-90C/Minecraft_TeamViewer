# SimMC Region Lua Adapter / SimMC 区域 Lua Adapter

## Entrypoints and Mod probe / 入口与 Mod 探测

`plugin.json` selects `main.lua` for known Fabric targets and `unsupported.lua` as the explicit
fallback. On a known Fabric target, `probe()` first checks `mods.is_loaded("smcmod")`; a missing
Mod reports `MOD_NOT_INSTALLED`. Unknown Fabric versions report `UNSUPPORTED_VERSION`, while a
platform with no implementation reports `NOT_IMPLEMENTED` instead of losing the capability.

`plugin.json` 为已知 Fabric 目标选择 `main.lua`，并以 `unsupported.lua` 作为明确后备入口。
已知 Fabric 目标先由 `probe()` 检查 `mods.is_loaded("smcmod")`；缺失 Mod 登记为
`MOD_NOT_INSTALLED`。未知 Fabric 版本登记 `UNSUPPORTED_VERSION`，尚未实现的平台登记
`NOT_IMPLEMENTED`，能力不会消失。

## Reflection handles / 反射句柄

The adapter resolves and caches `RegionManager.regionManager`,
`RegionManagerImpl.chunkToRegion`, `Region.color()` and `Region.isCore()` through
`java.field/java.method`. A missing class, field or method is retained verbatim and exposed as
`UNSUPPORTED_VERSION`. This is the section to update when SimMC changes package names or method
signatures.

Adapter 通过 `java.field/java.method` 解析并缓存 `RegionManager.regionManager`、
`RegionManagerImpl.chunkToRegion`、`Region.color()` 与 `Region.isCore()`。缺少类、字段或
方法时保留原始异常并登记 `UNSUPPORTED_VERSION`。SimMC 改包名或签名时，只需更新此段。

## Data conversion / 数据转换

`capture()` reads the manager's chunk-to-region map. It supports both public `ChunkPos.x/z`
fields and newer `x()/z()` accessors, converts the RGB integer to `#RRGGBB`, marks core chunks
with `╫`, and returns `coordinateSpace="absolute_chunk"`. The dimension comes from
`snapshots.world()`; common normalizes the absolute bounding box and owns network reporting.

`capture()` 读取区域管理器的区块映射，同时兼容公开 `ChunkPos.x/z` 字段和新版
`x()/z()` 方法；颜色整数转为 `#RRGGBB`，核心区块使用 `╫`，并返回
`coordinateSpace="absolute_chunk"`。维度来自 `snapshots.world()`；绝对边界归一化与网络
上报由 common 负责。

## Failure and lifecycle / 故障与生命周期

Handle initialization runs once on enable. A runtime capture exception is logged, stored and
changes the dynamic probe to `FAILED` with technical detail. The source creates no SimMC object
and mutates no region, so disable is a no-op. Do not add writes unless the plugin is switched to
an author-managed lifecycle with complete cleanup.

句柄在启用时只初始化一次。运行期采集异常会被记录，动态 probe 随后转为带技术详情的
`FAILED`。数据源不创建 SimMC 对象，也不修改区域，因此停用为空操作。若未来增加写入，
必须提供完整清理并改用 author-managed 生命周期。
