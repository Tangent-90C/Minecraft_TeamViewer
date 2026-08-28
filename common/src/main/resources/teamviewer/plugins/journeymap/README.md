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
`createClientWaypoint` on 26.1.2. The group renderer override is officially supported only on
Minecraft 26.1.2 with JourneyMap 6.0.5. Its optional Mixin companion validates JourneyMap's
renderer bytecode before applying; other or incompatible builds retain the public-API waypoint
integration and report that group renderer controls are unsupported.

JourneyMap 26.1/26.1.1 beta 没有逐路标显示控制，因此使用合并 API 2.x 入口；26.1.2
恢复了这些控制。26.2 又删除了 `createClientWaypoint`，完整控制入口会在 26.2 调用
`createWaypoint`，在 26.1.2 调用 `createClientWaypoint`。这些都是真实 API 兼容分支，
不是只放宽元数据。组级渲染覆盖目前正式支持 Minecraft 26.1.2 + JourneyMap 6.0.5；可选
Mixin 伴随层会在应用前校验 JourneyMap 渲染器字节码。其他版本或结构不兼容时仍保留公开
API 路标联动，并明确提示组级渲染控制不受支持。

## Remote-player conversion / 远程玩家转换

On full-control families, common invokes two projections with UUID-keyed TeamViewRelay player tables. Lua uses stable IDs
`player-marker:<uuid>` and `player-beacon:<uuid>`, floors block coordinates, converts the common
dimension string, and creates transient JourneyMap waypoints named `<name>`. API 2.x assigns them
to the online group, whose `[TV] Online` tag supplies the map label prefix. Marker mode shows on
the 2D map but not as a beacon; beacon mode shows in-world but not on the 2D map. The game UI has
four independent settings: online map markers, online world beacons, offline map markers, and
offline world beacons. Disabling any setting immediately removes only its owned objects. API 2.x
uses five persistent TeamViewRelay groups: online players, offline players, player reports,
web/admin reports, and other/legacy shared waypoints. The adapter waits for a nonblank JourneyMap
world ID and a nonempty loaded group store, then deterministically adopts one existing group by its
Relay role marker, legacy name, or legacy tag. It never deletes duplicate groups. New groups receive
a stable `teamviewer.group.role` marker, so later starts and world changes reuse them. Group creation
and assignment use only public API calls; an API mismatch falls back to the default ungrouped
waypoints without disabling the integration. API 1.x instead creates one
`player:<uuid>` native waypoint. Both stable capability IDs remain registered, but only one
projection owns the object. Reduced API families show only online and offline waypoint switches;
map/world visibility follows JourneyMap's global settings.

Minecraft 26.1.2 with JourneyMap 6.0.5 suppresses offscreen drawing only for Relay online and
offline player markers. Players inside the minimap viewport render normally, while the remaining
Relay player waypoints stay available to the fullscreen map without entering JourneyMap's
offscreen-label collision layout. Shared Relay waypoints and user-owned waypoints are unchanged.

在完整控制系列中，common 向两个 projection 传入以 UUID 为键的 TeamViewRelay 玩家表。Lua 使用稳定 ID
`player-marker:<uuid>`、`player-beacon:<uuid>`，向下取整方块坐标，转换 common 维度字符串，
并创建名称为 `<name>` 的临时 JourneyMap 路标。API 2.x 将其放入在线分组，由 `[TV] Online`
标签提供地图显示前缀。marker 模式只显示在二维地图，beacon 模式只显示在世界中；游戏内分别提供
在线地图标记、在线世界信标、离线地图标记和离线世界信标四个开关，
关闭任一项会立即删除它管理的对象。API 2.x 使用五个持久化 TeamViewRelay 分组：在线玩家、
离线玩家、玩家报点、网页/管理报点和其他/旧版共享路径点。适配器会等待 JourneyMap 提供非空
世界 ID 且已加载的分组仓库非空，然后依次按 Relay 角色标记、旧名称、旧标签确定性地复用一个
已有分组；不会删除重复分组。新分组会写入稳定的 `teamviewer.group.role` 标记，后续启动和切换
世界时会继续复用。分组只调用公开 API；API 不匹配时会回退为默认未分组路标，联动仍保持可用。
API 1.x 则只创建一个 `player:<uuid>` 原生路标；
两个稳定能力 ID 仍保留，但只有一个 projection 拥有对象。受限 API 只显示在线与离线路标两个开关，
地图/世界可见性遵循 JourneyMap 全局设置。

Minecraft 26.1.2 + JourneyMap 6.0.5 只对 Relay 在线与离线玩家标记禁止离屏绘制。小地图视口内的
玩家正常显示，其余 Relay 玩家路径点仍可在全屏地图查看，但不会进入 JourneyMap 的离屏标签碰撞布局；
共享 Relay 路标和用户自有路标不受影响。

Offline records use independent transient marker and beacon waypoints in the offline group on
full-control APIs. Its `[TV] Offline` tag is the only map label prefix; the waypoint name contains
the player and last-seen time. When common has a resolved local relation for the offline UUID,
their waypoint color follows that relation; otherwise the existing orange last-seen color remains.

完整控制 API 的离线记录在离线分组中使用独立的临时标记与信标路标。`[TV] Offline` 标签是唯一的
地图显示前缀；路标名称只包含玩家名和最后在线时间。当 common 已为离线 UUID 解析出本地关系时，
路标颜色采用该关系；未解析时保留原有橙色的最后位置颜色。

## Shared-waypoint conversion / 共享路标转换

`list_local()` reads JourneyMap's current waypoints and excludes objects whose `modId` is
`teamviewer`, preventing objects written by the relay from returning as new local marks. It maps
JourneyMap GUID, name, position, primary dimension and color into the common local-waypoint table.
`upsert_remote` performs the reverse conversion for a common command and records the object by
`waypointId`; delete/clear use that ownership table only. `quick` and `manual` kinds enter the
player-report group; `web_map_tactical` and `admin_tactical` provenance enter the web/admin group;
unknown and legacy provenance enter the other shared-waypoint group.

`list_local()` 读取 JourneyMap 当前路标，并排除 `modId=teamviewer` 的对象，避免中继写入
对象再次作为本地新标点返回。它把 JourneyMap GUID、名称、位置、主维度和颜色转换为
common 本地路标表。`upsert_remote` 对 common 命令执行反向转换，并按 `waypointId` 记录
对象；删除与清理只使用该所有权表。`quick`、`manual` 类型进入玩家报点组；来源为
`web_map_tactical`、`admin_tactical` 的路径点进入网页/管理报点组；未知和旧版来源进入其他组。

## Group beacon policy / 分组信标策略

On the supported 26.1.2 renderer, each Relay group independently controls world waypoint rendering,
rotating beam, static beam, and maximum distance. Defaults are `false`, `false`, `false`, and 512
blocks. These values fully replace the four matching JourneyMap globals for Relay-owned groups only;
unmanaged JourneyMap waypoints continue to use the user's global settings. Policies live in the
plugin companion for the current play session and do not rewrite JourneyMap's global or per-group
user configuration.

在受支持的 26.1.2 渲染器中，每个 Relay 分组分别控制“渲染世界路径点”“旋转光束”
“静止光束”和“最大距离”，默认值依次为关闭、关闭、关闭和 512 方块。这些值只对 Relay
创建的分组完整覆盖 JourneyMap 对应的四项全局设置；其他 JourneyMap 路标仍遵循用户的全局
设置。策略只在当前游玩会话中由插件伴随层保存，不会重写 JourneyMap 的全局或分组用户配置。

## Lifecycle and adapting another API / 生命周期与新版适配

`on_disable` clears all relay waypoint ownership maps and removes their JourneyMap objects, but
retains the five persistent Relay groups and all duplicate groups for the user to manage.
Setting changes immediately clear the newly disabled presentation. To support a new
JourneyMap/Minecraft release, copy the closest version entry, update only mapped class names and
changed API calls, add a manifest entrypoint, and keep capability IDs, common fields, ownership
rules and probe semantics unchanged.

`on_disable` 会清空全部中继路标所有权表并移除对应 JourneyMap 对象，但保留五个持久化 Relay
分组以及所有重复分组，由用户自行管理；设置关闭时立即清理相应展示。适配新的
JourneyMap/Minecraft 版本时，应复制最接近的版本入口，只更新变化的 JourneyMap API 调用，
再新增清单入口；Minecraft 映射对象继续使用稳定服务，能力 ID、common 字段、所有权规则和
probe 语义必须保持不变。
