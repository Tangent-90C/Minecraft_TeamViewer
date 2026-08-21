# Xaero World Map and Minimap Lua Adapter / Xaero 世界地图与小地图 Lua Adapter

## Independent capabilities / 独立能力

The manifest declares `xaero-worldmap` (`remote-player`) and `xaero-minimap`
(`shared-waypoint`) separately. Their probes independently check `xaeroworldmap` and
`xaerominimap`, so installing only one Mod yields one working capability and one explicit
`MOD_NOT_INSTALLED` record. Pinned module-session artifacts use `main.lua`; the old
`getWaypointsManager/getCurrentSet/getList` shape uses `legacy-1.19.3.lua`. Unknown Fabric versions and
unimplemented platforms use the status-registering fallback.

清单分别声明 `xaero-worldmap`（`remote-player`）和 `xaero-minimap`（`shared-waypoint`）。
两个 probe 独立检查 `xaeroworldmap` 与 `xaerominimap`，因此只安装一个 Mod 时，一个能力
正常工作，另一个明确登记 `MOD_NOT_INSTALLED`。固定的 module-session 工件使用 `main.lua`；
旧版 `getWaypointsManager/getCurrentSet/getList` 结构使用 `legacy-1.19.3.lua`；未知
Fabric 版本与未实现平台使用只登记状态的后备入口。

## World Map tracker / 世界地图玩家追踪

Lua resolves Xaero tracker interfaces and the common Java record classes once. It creates
`ITrackedPlayerReader` and `IPlayerTrackerSystem` proxies, registers a stable tracker ID, and
keeps one Java `ArrayList` as Xaero's iterator source. Every TeamViewRelay callback clears and
rebuilds that list from the common player fields `uuid`, `name`, `dimension`, and
`position{x,y,z}`. Common already removes the local player and other dimensions.
External classes and proxy interfaces are resolved by the Fabric Mod classloader. Native
dimension keys come from `minecraft.client_objects`, so the Lua source contains no Yarn class
name that would fail after production remapping.

Lua 一次性解析 Xaero tracker 接口与 common Java record 类，创建
`ITrackedPlayerReader`、`IPlayerTrackerSystem` proxy，以稳定 tracker ID 注册，并维护一个
供 Xaero 迭代的 Java `ArrayList`。每次 TeamViewRelay 回调都按 `uuid`、`name`、
`dimension`、`position{x,y,z}` 重建列表；本地玩家和其他维度已由 common 过滤。
外部类和 proxy 接口通过 Fabric Mod classloader 解析；原生维度键由
`minecraft.client_objects` 提供，因此 Lua 不包含正式重映射后会失效的 Yarn 类名。

A missing tracker class is `UNSUPPORTED_VERSION`; a registration/API invocation failure becomes
`FAILED` and retains the exception. Xaero exposes `register` but no matching `unregister`, so the
plugin deliberately uses `hotToggle: restart`: enabling or disabling the plugin itself applies on
the next client start instead of leaving a stale proxy behind.

缺少 tracker 类时登记 `UNSUPPORTED_VERSION`；注册或 API 调用失败时登记 `FAILED` 并保留
异常。Xaero 只提供 `register`，没有对应 `unregister`，所以本插件明确使用
`hotToggle: restart`；插件本身的启停显示 `PENDING_RESTART` 并在下次启动生效，避免遗留旧 proxy。

## Display settings / 显示开关

The game UI provides three immediate settings matching Xaero's actual outputs: online players on
the World Map, offline players on the World Map, and offline last-seen players on the Minimap.
Turning a World Map setting off clears its tracker list; turning the Minimap setting off removes
only TeamViewRelay-owned `[TV Last]` waypoints. Shared Relay waypoints and user waypoints are not
affected.

游戏内提供三个即时生效、与 Xaero 实际输出对应的开关：世界地图中的在线玩家、世界地图中的离线玩家、
以及小地图中的离线最后位置。关闭世界地图开关会清空相应 tracker；关闭小地图开关只移除
TeamViewRelay 管理的 `[TV Last]` 路标，不会影响共享 Relay 路标或用户自己的路标。

## Minimap waypoint bridge / 小地图路标桥

`list_local()` walks the current Xaero waypoint set and returns
`{nativeId,name,symbol,x,y,z,dimension,color}`. It excludes entries in the managed-object table
and names with the `[TV] ` prefix, preventing relay feedback loops. `upsert_remote` converts a
common waypoint command into Xaero's six-argument `Waypoint`, replaces the previous managed
object, and saves the world. Delete and clear touch only entries recorded by this plugin.

`list_local()` 遍历当前 Xaero 路标集并返回
`{nativeId,name,symbol,x,y,z,dimension,color}`。它排除 managed 表内对象和带 `[TV] ` 前缀
的名称，避免中继回环。`upsert_remote` 把 common 路标命令转换成 Xaero 六参数
`Waypoint`，替换旧 managed 对象并保存世界；删除与清理只处理本插件记录的对象。

Offline records use separately owned `[TV Last]` waypoints. A resolved local relation controls
their color; an unclassified record keeps the legacy orange color. Xaero World Map receives the
same offline positions through its tracker but has no stable tracker color or label API.

离线记录使用独立管理的 `[TV Last]` 路标。已解析的本地关系决定其颜色；未分类记录保留原有橙色。Xaero
World Map 也会通过 tracker 接收同一离线位置，但其稳定 tracker API 没有颜色或标签接口。

When adapting a new Xaero release, update class names, session traversal and constructor shape
inside the handle/context sections. Do not move player filtering, shared-waypoint conflict policy
or synchronization timing out of common.

适配新版 Xaero 时，只更新句柄、会话遍历和构造器签名。玩家过滤、共享路标冲突策略与
同步时序仍应留在 common。
