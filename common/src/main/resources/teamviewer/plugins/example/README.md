# TeamViewRelay Lua Adapter Reference / Lua Adapter 标杆示例

This built-in package is an executable, side-effect-free API reference. It is read-only and
disabled by default. Use **Copy as custom plugin / 复制为自定义插件** in the management page,
then edit the generated copy under `config/team-view-relay/plugins/`.

本内置包是一份可执行、无副作用的完整 API 参考。它只读且默认关闭。请在管理页使用
**复制为自定义插件**，然后编辑 `config/team-view-relay/plugins/` 下生成的副本。

## 1. Integration boundary / 对接边界

The recommended data flow is:

```text
TeamViewRelay common snapshot or callback
  -> Lua version/external-mod adapter
  -> external mod API, service or reflection
```

Data read from an external mod travels in the opposite direction. Lua converts it into one of
the stable common result tables, while common keeps ownership of scheduling, synchronization,
deduplication, battle-map history/projection, heartbeat and network protocol.

推荐数据流是“TeamViewRelay common 快照或回调 → Lua 版本/外部 Mod Adapter → 外部 Mod
API、宿主服务或反射”。从外部 Mod 读取的数据按相反方向转换成稳定的 common 返回表；
调度、同步、去重、战局地图历史定位/投影、心跳和网络协议仍由 common 负责。

## 2. Manifest and entrypoints / 清单与入口

`plugin.json` declares metadata, dependencies, dynamic settings and every capability in
`provides`. A script may register exactly those IDs with exactly those roles—no omissions,
extras or role changes. `entrypoints[]` may select loader/version-specific scripts; the single
`entry` field remains compatible with older packages. `documentation` points to this README.

`plugin.json` 声明元数据、依赖、动态设置及 `provides` 中的全部能力。脚本只能按完全一致
的 ID 和角色注册这些能力，不能遗漏、额外注册或改变角色。`entrypoints[]` 可选择加载器/
版本专用脚本；旧包仍可只使用 `entry`。`documentation` 指向本 README。

The example declares all setting types: `boolean`, `integer`, `number`, `string`, `enum` and
`color`. At runtime their normalized values are available as `settings.<key>`.

示例声明了全部设置类型：`boolean`、`integer`、`number`、`string`、`enum` 和 `color`；
运行时可通过 `settings.<key>` 读取规范化后的值。

## 3. Host discovery APIs / 宿主探测接口

- `environment.loader_id()`, `minecraft_version()`, `mod_version(id)` describe the runtime.
- `mods.is_loaded(id)` distinguishes a missing optional Mod before touching its classes.
- `services.get(id)` obtains an object published by a thin Java entrypoint. Fetch dynamic
  services inside `probe()` or each operation; do not assume they exist when Lua first loads.
- `services.get("minecraft.client_objects")` supplies `blockPosition(x,y,z)` and
  `dimensionKey(id)`. Use it instead of hard-coding mapped `net.minecraft.*` class names.
- `snapshots.world/players/waypoints/scoreboard()` provide read-only, loader-neutral tables.
- `tv.log.info/warn/error(message)` writes plugin-scoped diagnostics.

- `environment.loader_id()`、`minecraft_version()`、`mod_version(id)` 描述当前环境。
- 先用 `mods.is_loaded(id)` 区分 Mod 缺失，再访问外部类。
- `services.get(id)` 获取薄 Java 入口发布的对象。动态服务应在 `probe()` 或每次操作中获取，
  不能假定 Lua 加载时入口已经就绪。
- `services.get("minecraft.client_objects")` 提供 `blockPosition(x,y,z)` 与
  `dimensionKey(id)`；不要硬编码经过映射的 `net.minecraft.*` 类名。
- `snapshots.world/players/waypoints/scoreboard()` 提供只读、与加载器无关的表。
- `tv.log.info/warn/error(message)` 输出带插件范围的诊断日志。

## 4. Reflection bridge / 反射桥

`java.type(className)` resolves a Java class. `java.method(owner, name, parameterTypes...)` and
`java.field(owner, name)` resolve and cache handles. `java["new"](class, ...)` constructs an
object, and `java.proxy(interfaceName, callbackTable)` implements a Java interface in Lua.
Call a bound static Java method with Lua's colon syntax, for example
`java.type("java.lang.Integer"):parseInt("7")`.
Resolve handles once, keep the original exception, and expose a truthful `UNSUPPORTED_VERSION`
or `FAILED` probe result if a signature no longer matches.
The host resolves these operations through the active Fabric/NeoForge Mod classloader, so
optional-Mod classes and interfaces need not be visible to the JVM system classloader.

`java.type(className)` 解析 Java 类；`java.method(owner, name, parameterTypes...)` 与
`java.field(owner, name)` 解析并缓存句柄；`java["new"](class, ...)` 创建对象；
`java.proxy(interfaceName, callbackTable)` 用 Lua 实现 Java 接口。应只初始化一次句柄，保留
调用绑定类的静态方法时使用 Lua 冒号语法，例如
`java.type("java.lang.Integer"):parseInt("7")`。
原始异常，并在签名不匹配时通过 probe 如实返回 `UNSUPPORTED_VERSION` 或 `FAILED`。
这些操作统一通过当前 Fabric/NeoForge Mod classloader 解析，外部 Mod 类和接口不需要位于
JVM system classpath 中。

## 5. Adapter contracts / Adapter 契约

`register_remote_player_projection` receives `sync(players, enabled)`. `players` is keyed by
UUID and each value contains `uuid`, `name`, `dimension`, and `position{x,y,z}`. `clear()` must
remove only objects owned by this plugin.

`register_remote_player_projection` 的 `sync(players, enabled)` 接收以 UUID 为键的玩家表；
每项含 `uuid`、`name`、`dimension`、`position{x,y,z}`。`clear()` 只能清理本插件对象。

`register_shared_waypoint_adapter` returns local waypoints from `list_local()` as
`{nativeId,name,symbol,x,y,z,dimension,color}`. Its write callbacks receive the common command
fields `waypointId,name,symbol,x,y,z,dimension,color`. Filter out plugin-owned remote objects to
prevent synchronization loops.

`register_shared_waypoint_adapter` 的 `list_local()` 返回
`{nativeId,name,symbol,x,y,z,dimension,color}`；写入回调接收
`waypointId,name,symbol,x,y,z,dimension,color`。必须过滤插件自建对象，避免同步回环。

`register_battle_map_source.capture()` returns `nil` when there is no observation, otherwise:

```lua
{
  dimension = "minecraft:overworld",
  observedAt = 0,
  coordinateSpace = "relative_to_player", -- or "absolute_chunk"
  mapSize = 0, anchorRow = 0, anchorColumn = 0,
  cells = {{x = 0, z = 0, symbol = "", color = "#FFFFFF"}}
}
```

战局地图没有观测时返回 `nil`；有数据时按上表返回。相对坐标使用
`relative_to_player`，绝对区块坐标使用 `absolute_chunk`。

Every adapter may expose `probe()` returning `{status, detail}`. Supported states are
`AVAILABLE`, `MOD_NOT_INSTALLED`, `UNSUPPORTED_VERSION`, `NOT_IMPLEMENTED`,
`ENTRYPOINT_NOT_READY` and `FAILED`. `tv.register_unavailable_capability` formally registers a
declared unavailable variant. Never omit it. `tv.use_native_capability` only aliases an existing
Java implementation; it is not an external-Mod Adapter implementation.

每类 Adapter 都可提供返回 `{status, detail}` 的 `probe()`。支持状态包括 `AVAILABLE`、
`MOD_NOT_INSTALLED`、`UNSUPPORTED_VERSION`、`NOT_IMPLEMENTED`、
`ENTRYPOINT_NOT_READY`、`FAILED`。不可用变体应通过
`tv.register_unavailable_capability` 正式登记，不能省略。`tv.use_native_capability` 只是
复用已有 Java 实现，并不等同于完成外部 Mod Adapter。

## 6. Lifecycle and safety / 生命周期与安全

Use `tv.on_enable`, `tv.on_disable` and `tv.on_settings_changed`. Make cleanup idempotent; keep
stable IDs for external objects; do not delete objects owned by users or other plugins. A managed
plugin can be toggled immediately only when its callbacks and `on_disable` can fully clean up.
After three consecutive uncaught callback errors the host suspends it.

使用 `tv.on_enable`、`tv.on_disable`、`tv.on_settings_changed`。清理必须可重复执行；外部
对象应使用稳定 ID；不能删除用户或其他插件的对象。只有回调和 `on_disable` 能完整清理的
managed 插件才能即时启停。连续三次未捕获回调错误后宿主会暂停插件。
若外部 API 只能注册、不能反注册回调，应把 `hotToggle` 声明为 `restart`。

An adapter may call `tv.configure_setting({key, visible, enabled, detail})` for a setting declared
in its manifest. This changes only the runtime UI state; persisted values remain independent.
Plugins that do not call it retain the compatible default: every declared setting is visible and enabled.

Adapter 可对清单中已声明的设置调用
`tv.configure_setting({key, visible, enabled, detail})`。它只改变运行时 UI 状态，不会覆盖已保存的值。
未调用的第三方插件保持向后兼容：所有声明设置均可见、可编辑。

`main.lua` registers three no-op adapters. Enabling this example returns empty collections or
`nil`, creates no map object, reads no external data and uploads nothing. Replace its IDs and
no-op bodies only in a copied package.

`main.lua` 注册了三个空操作 Adapter。启用本示例只会返回空集合或 `nil`，不会创建地图
对象、读取外部数据或上传内容。请只在复制后的包中替换 ID 与空操作实现。
