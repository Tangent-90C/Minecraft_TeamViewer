# JourneyMap Lua Adapter / JourneyMap Lua Adapter 教程

## Thin Java service / 薄 Java 服务

JourneyMap discovers integrations through an annotated Java `IClientPlugin`. That class only
receives and stores `IClientAPI`; the runtime publishes the current value as
`journeymap.client_api`. It contains no marker, beacon, waypoint conversion or synchronization
logic. Lua calls `services.get("journeymap.client_api")` dynamically instead of caching `nil`,
allowing `ENTRYPOINT_NOT_READY` to become `AVAILABLE` after JourneyMap initializes.

JourneyMap 通过带注解的 Java `IClientPlugin` 发现集成。该类只接收并保存 `IClientAPI`，
运行时把当前值发布为 `journeymap.client_api`；其中不包含标记、信标、路标转换或同步逻辑。
Lua 动态调用 `services.get("journeymap.client_api")`，不缓存 `nil`，因此 JourneyMap 初始化
后可从 `ENTRYPOINT_NOT_READY` 自动恢复为 `AVAILABLE`。

## Version entrypoints and handles / 版本入口与句柄

The exact-version matrix selects API 1.x (`fabric-api-v1.lua`), API 2.x without presentation
controls (`fabric-1.21.8.lua`), or API 2.x with full controls (`fabric-26.1.2.lua`). Each entry resolves JourneyMap's
`WaypointFactory` through the Mod classloader and obtains mapped block positions/dimension keys
from `minecraft.client_objects`. The fallback explicitly reports
`UNSUPPORTED_VERSION` on an unknown Fabric release and `NOT_IMPLEMENTED` on another loader.
Missing JourneyMap is always `MOD_NOT_INSTALLED`; a class/API mismatch retains the original error
as `UNSUPPORTED_VERSION`.

精确版本矩阵分别选择 API 1.x（`fabric-api-v1.lua`）、无独立显示控制的 API 2.x
（`fabric-1.21.8.lua`）和完整控制 API 2.x（`fabric-26.1.2.lua`）。每个入口通过 Mod classloader 解析 JourneyMap
`WaypointFactory`，并从 `minecraft.client_objects` 获取映射后的方块坐标与维度键。
后备入口在未知 Fabric 版本登记
`UNSUPPORTED_VERSION`，在其他加载器登记 `NOT_IMPLEMENTED`。缺少 JourneyMap 时为
`MOD_NOT_INSTALLED`；类/API 不匹配时保留原始异常并登记 `UNSUPPORTED_VERSION`。

JourneyMap 26.1/26.1.1 beta lacks per-waypoint presentation controls and therefore uses the merged
API 2.x entry. JourneyMap 26.1.2 restores those controls, while 26.2 removes
`createClientWaypoint`; the full-control entry selects `createWaypoint` on 26.2 and
`createClientWaypoint` on 26.1.2. These are real API compatibility branches rather than
metadata-only version widening.

JourneyMap 26.1/26.1.1 beta 没有逐路标显示控制，因此使用合并 API 2.x 入口；26.1.2
恢复了这些控制。26.2 又删除了 `createClientWaypoint`，完整控制入口会在 26.2 调用
`createWaypoint`，在 26.1.2 调用 `createClientWaypoint`。这些都是真实 API 兼容分支，
不是只放宽元数据。

## Remote-player conversion / 远程玩家转换

On full-control families, common invokes two projections with UUID-keyed TeamViewRelay player tables. Lua uses stable IDs
`player-marker:<uuid>` and `player-beacon:<uuid>`, floors block coordinates, converts the common
dimension string, and creates transient `[TV] <name>` JourneyMap waypoints. Marker mode shows on
the 2D map but not as a beacon; beacon mode shows in-world but not on the 2D map. The two plugin
settings gate them independently. API 1.x and reduced API 2.x instead create one `player:<uuid>`
native waypoint. Both stable capability IDs remain registered, but only one projection owns the
object. Their UI shows only `show_remote_players`; map/world visibility follows JourneyMap's global settings.

在完整控制系列中，common 向两个 projection 传入以 UUID 为键的 TeamViewRelay 玩家表。Lua 使用稳定 ID
`player-marker:<uuid>`、`player-beacon:<uuid>`，向下取整方块坐标，转换 common 维度字符串，
并创建临时 `[TV] <name>` JourneyMap 路标。marker 模式只显示在二维地图，beacon 模式只
显示在世界中；两个插件设置分别控制它们。API 1.x 与缩减版 API 2.x 只创建一个
`player:<uuid>` 原生路标；两个稳定能力 ID 仍保留，但只有一个 projection 拥有对象。
UI 也只显示 `show_remote_players`，地图/世界可见性遵循 JourneyMap 全局设置。

Offline records use independent transient `[TV Last]` waypoints. When common has a resolved
local relation for the offline UUID, their waypoint color follows that relation; otherwise the
existing orange last-seen color remains.

离线记录使用独立的临时 `[TV Last]` 路标。当 common 已为离线 UUID 解析出本地关系时，路标颜色采用该关系；
未解析时保留原有橙色的最后位置颜色。

## Shared-waypoint conversion / 共享路标转换

`list_local()` reads JourneyMap's current waypoints and excludes objects whose `modId` is
`teamviewer`, preventing objects written by the relay from returning as new local marks. It maps
JourneyMap GUID, name, position, primary dimension and color into the common local-waypoint table.
`upsert_remote` performs the reverse conversion for a common command and records the object by
`waypointId`; delete/clear use that ownership table only.

`list_local()` 读取 JourneyMap 当前路标，并排除 `modId=teamviewer` 的对象，避免中继写入
对象再次作为本地新标点返回。它把 JourneyMap GUID、名称、位置、主维度和颜色转换为
common 本地路标表。`upsert_remote` 对 common 命令执行反向转换，并按 `waypointId` 记录
对象；删除与清理只使用该所有权表。

## Lifecycle and adapting another API / 生命周期与新版适配

`on_disable` clears all three ownership maps and removes their JourneyMap objects. Setting changes
immediately clear the newly disabled presentation. To support a new JourneyMap/Minecraft release,
copy the closest version entry, update only mapped class names and changed API calls, add a
manifest entrypoint, and keep capability IDs, common fields, ownership rules and probe semantics
unchanged.

`on_disable` 会清空三个所有权表并移除对应 JourneyMap 对象；设置关闭时立即清理相应展示。
适配新的 JourneyMap/Minecraft 版本时，应复制最接近的版本入口，只更新变化的 JourneyMap
API 调用，再新增清单入口；Minecraft 映射对象继续使用稳定服务，能力 ID、common 字段、
所有权规则和 probe 语义必须保持不变。
