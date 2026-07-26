# NodeMC Scoreboard Lua Adapter / NodeMC 计分板 Lua Adapter

## Data entry / 数据入口

`snapshots.scoreboard()` is produced by the Minecraft-version bridge and contains
`dimension`, `observedAt`, and ordered `lines`. Each line has `rawText` plus styled `runs` with
`text` and `colorRaw`. Lua never accesses loader-specific scoreboard classes.

`snapshots.scoreboard()` 由 Minecraft 版本桥生成，包含 `dimension`、`observedAt` 以及有序
`lines`；每行含 `rawText` 和带 `text`、`colorRaw` 的样式片段 `runs`。Lua 不直接访问
加载器专用计分板类。

## Parsing and conversion / 解析与转换

`main.lua` iterates UTF-8 code points and accepts the Box Drawing, Block Elements, Geometric
Shapes and Miscellaneous Symbols ranges used by the former common parser. It reads the `left 0
right` range hint when present, locates a square of equal-length glyph rows, finds the player
anchor `┼`, and converts every other glyph to a relative chunk cell.

`main.lua` 遍历 UTF-8 字符，只接受原 common 解析器使用的制表线、方块元素、几何形状和
杂项符号范围；如存在则读取 `left 0 right` 范围提示，寻找等长字符组成的方阵，定位玩家
锚点 `┼`，最后把其余字符转换成相对区块单元格。

The returned table uses `coordinateSpace="relative_to_player"`; `x/z` are offsets from the
anchor, while `mapSize`, `anchorRow`, `anchorColumn`, color and observation time are retained.
Common then performs position-history matching, projection, semantic deduplication, heartbeat and
protocol upload. Those responsibilities must not be copied into Lua.

返回表使用 `coordinateSpace="relative_to_player"`；`x/z` 是相对锚点的偏移，同时保留
地图大小、锚点行列、颜色与观测时间。之后由 common 完成历史位置匹配、投影、语义去重、
心跳和协议上传，不应在 Lua 中重复实现。

## Registration and lifecycle / 注册与生命周期

The plugin declares and registers the stable capability `nodemc-scoreboard-battle-map` as a
`battle-map-source`. Its probe is always `AVAILABLE` because the loader-neutral scoreboard
snapshot exists even outside NodeMC; `capture()` simply returns `nil` when no matching map is
visible. It owns no external objects, so disable cleanup is intentionally empty.

插件以 `battle-map-source` 声明并注册稳定能力 `nodemc-scoreboard-battle-map`。由于所有
环境都能提供加载器无关的计分板快照，probe 始终为 `AVAILABLE`；未识别到地图时
`capture()` 返回 `nil`。本插件不持有外部对象，因此停用清理有意为空。

To support another server format, change only glyph filtering, range detection and square
selection. Keep the manifest ID, role and common result fields stable if it remains the NodeMC
source; use a new plugin/capability ID for a different server integration.

适配其他服务器格式时，只修改字符过滤、范围识别和方阵选择。若仍是 NodeMC 数据源，应
保持清单 ID、角色和 common 返回字段不变；不同服务器联动应使用新的插件与能力 ID。
