# Changelog

## v0.8.4-proto0.7.0 - 2026-08-22

- JourneyMap 在线玩家、离线玩家、玩家报点、网页/管理端报点和其他共享路标分别使用五个独立路径点组；
  新组采用稳定 GUID，并按 GUID、角色元数据、旧名称和旧标签依次复用已有组，避免每次启动继续创建同名空组。
- 已存在的重复路径点组不会被自动删除；用户可以在 JourneyMap 中自行清理，插件会确定性地保留并复用一个匹配组。
- Minecraft 26.1.2 的 JourneyMap 插件支持对每个 Relay 路径点组分别控制世界路径点、旋转光束、静止光束
  和最大显示距离，不修改 JourneyMap 的全局客户端设置；不支持该接口的版本会提示并回退到原有实现。
- 修复 Fabric All-in-One 根元数据遗漏 JourneyMap Mixin 配置，导致组级信标选项全部禁用的问题；构建现在会校验
  Mixin 配置插件和目标类均存在于对应内嵌 Adapter。
- 修复 JourneyMap 在有效游戏会话中返回空 `worldId` 或空路径点组列表时，三个联动能力被错误标记为
  “外部入口未就绪”的问题；路径点组存储重建后仍会恢复相同稳定 GUID。
- 修复打开集成插件页面时，JourneyMap 能力探测每帧抓取并反射展开完整世界、玩家和实体快照而卡死渲染线程的问题。
  Lua 宿主新增轻量 `environment.play_session_ready()` 生命周期接口，能力探测不再读取完整世界快照。
- JourneyMap 内置插件版本升级为 `1.4.2`，Mod 版本升级为 `v0.8.4`；网络协议仍为 `0.7.0`，
  协议子模块、后端和网页脚本无需升级。

## v0.8.2-proto0.7.0 - 2026-08-22

- 大量离线玩家记录改为按维度的空间索引；世界渲染只枚举渲染距离附近的候选项，并缓存离线玩家标签。
  新建配置的渲染距离默认值调整为 512，已有配置文件中的用户值保持不变。
- 在线玩家、离线最后位置和 Tab 历史分别发布不可变版本化快照。无关实体/路标更新、重复离线记录和
  未变化的 Tab 不再触发全量筛选、关系分类或原生地图同步。
- Tab 历史查询会记住本连接中已完成的空结果，避免对同一批离线 UUID 重复请求；历史实际变化后才刷新关系。
- JourneyMap、Xaero 等原生地图投影拆分在线与离线更新通道：在线位置高频变化不会再重建离线玩家标记。
- Lua 热路径为常用玩家、坐标和关系类型采用显式转换，避免逐字段反射；修复极端世界坐标下空间索引遍历的整数溢出。
- Mod 版本升级为 `v0.8.2`；网络协议保持 `0.7.0`，协议子模块、后端和网页脚本无需升级。

## v0.8.1-proto0.7.0 - 2026-08-22

- “Tab 标签敌我识别”插件可将本城、友城、敌对/交战城镇、友方成员和原始采集时间复制为
  版本化 JSON 关系档案，供 Web 在本机导入；档案不会写入 TeamViewRelay 房间。
- Lua 插件 API 新增受大小、深度和条目数限制的 `tv.copy_json_to_clipboard(table)`，各 Fabric 与
  NeoForge Adapter 通过对应 Minecraft 原生剪贴板实现该能力。
- JourneyMap API v2 会创建持久化的 `TeamViewRelay Online Players`（`[TV] Online`）和
  `TeamViewRelay Offline Players`（`[TV] Offline`）路径点组；不支持路径点组的 API 会回退到默认路径点，
  不影响地图联动。
- JourneyMap 26.1.2 及同类完整 API 将在线/离线 Relay 玩家分别拆分为地图标记与世界信标四个即时开关。
  修复此前关闭在线标记和信标后，离线 `[TV Last]` 路标仍留在地图上的问题。
- JourneyMap 旧 API 会回退为在线与离线路标两个有效开关，避免显示无法兑现的地图/世界细分选项。
- Xaero 地图联动新增三个即时开关：在线玩家世界地图、离线玩家世界地图、离线玩家小地图最后位置；
  关闭时仅清理由 TeamViewRelay 管理的 tracker 或 `[TV Last]` 路标，不影响用户路径点和共享 Relay 路标。
- Mod 版本升级为 `v0.8.1`；网络协议仍为 `0.7.0`，后端和协议仓库无需升级。

## v0.7.0-proto0.6.5 - 2026-08-20

- 公共 API 增加远程玩家坐标来源、来源类型、显示名称和位置分辨率。
- 网络协议升级为 `0.6.5`，最低兼容版本仍为 `0.6.1`。
- 完整快照和增量更新均解码并保留来源元数据，供 AimAssist 等客户端判断数据可信度。
- `TeamViewRelayApi.API_VERSION` 升级为 3，同时保留旧的 `RemotePlayerSnapshot` 构造器兼容二进制调用。

本文件记录 TeamViewRelay Mod 的用户可见变更。版本号格式为
`v<Mod 版本>-proto<协议版本>`；协议版本未变化时，后端和网页脚本不需要跟随 Mod 一起发布。

## v0.6.2-proto0.6.4 - 2026-08-20

### 离线玩家显示修复

- 网络协议继续使用 UTC 毫秒时间，游戏内世界渲染、JourneyMap 和 Xaero 标记改为按客户端本地时区显示。
- 修复 Xaero 离线玩家标记在断开连接、切换世界或 waypoint 会话后无法删除的问题。
- 修复重新上线后仍显示自己的离线历史标记的问题，并保留用户自有 waypoint。

## v0.6.1-proto0.6.4 - 2026-08-19

### 离线玩家最后位置

- 接收权威 Squaremap 外部数据源维护的离线玩家最后位置和 Unix UTC 毫秒时间；该功能默认关闭。
- 世界渲染可分别显示橙色方框和追踪线，并在 512 方块内以批量矢量线段显示姓名与 UTC 时间，
  避免远距离历史玩家带来持续的文字渲染开销。
- JourneyMap、Xaero Minimap 和 Xaero World Map 使用独立标记显示离线历史；玩家重新上线、断线、
  切换世界或关闭功能时会清理历史标记，不与实时玩家对象混用。
- Common SDK API 升级至 v2，新增 `TeamViewRelayApi.lastSeenPlayers()` 不可变只读快照。

### 协议与兼容性

- 网络协议升级为 `0.6.4`，最低兼容版本仍为 `0.6.1`。
- 协议低于 `0.6.4` 的客户端不会收到离线历史 scope；实时玩家、实体、路标和战局数据继续兼容。
- 历史玩家使用独立仓库、投影能力与渲染规划，不改变现有在线玩家的过期和残留清理语义。

## v0.6.0-proto0.6.2 - 2026-08-18

### 敌我识别与扩展 API

- Tab 标签敌我识别支持从 `/town` 和 `/t` 的聊天输出自动导入城镇、盟友、敌对与居民关系；
  插件页显示当前自动识别结果、采集时间和清空操作。
- 新增手动/自动关系采用模式，默认“仅自动”，手动标签配置与自动采集结果彼此独立保存。
- Common 对外提供稳定的远程玩家、关系来源和可攻击性只读 API，其他 Mod 无需依赖网络或 UI 实现。

### 性能与架构

- Fabric 1.18.2-1.21.10 与 NeoForge 1.20.2-1.21.10 将方框、追踪线、圆、光柱和平面按渲染状态批量提交，
  draw call 数量不再随远程玩家数量线性增长。
- Minecraft 1.21.11 和 26.x 保留原生 Gizmo 管线，并使用自定义 `BatchGizmo` 按遮挡模式合并命令。
- 配置页与插件管理页拆分为独立会话；GameClient、RuntimeGateway、事件注册、配置/插件页面和渲染器采用
  core/shim 分层，删除大量版本复制代码并强化 Adapter SDK 边界。
- Mod 版本升级为 `v0.6.0`；网络协议仍为 `0.6.2`，最低兼容版本仍为 `0.6.1`。

## v0.5.0-proto0.6.2 - 2026-08-04

比较范围：[`v0.4.14-proto0.6.2`（`f599b1c`）](https://github.com/MC-TeamViewer/Minecraft_TeamViewer/commit/f599b1c6aedd7e8150c44485d6ba3d1d1c826461)
至 `v0.5.0-proto0.6.2`。

### Minecraft、Loader 与发布产物

- Fabric 从只验证 Minecraft 1.21.8 扩展为覆盖 `1.18`–`26.2` 之间的全部 31 个正式版本。
  17 个独立 Jar 按真实 API 兼容范围划分，同时提供一个覆盖全部 Fabric 目标的 All-in-One。
- 新增 NeoForge 支持，19 个独立 Jar 覆盖 Minecraft `1.20.2`–`26.2` 的全部 21 个正式版本；
  补齐仅有 beta NeoForge 的 `1.20.3`、`1.20.5`、`1.21.2`、`1.21.6`、`1.21.7` 和 `1.21.9`。
  `1.20.1` 留给后续独立 Forge 版本，不引入旧 Forge 命名空间或双 FML 入口。
- 新增实验性的 NeoForge All-in-One。一个 Java 17 外壳按运行时版本选择唯一的重定位
  `GAMELIBRARY` adapter、Factory 和 Mixin；19 个独立 Jar 继续作为回退选择。
- NeoForge beta 目标分离构建 pin、玩家运行范围和发布渠道；玩家可更新同一 Minecraft 版本线的
  Loader，定时 CI 会从官方 Maven 元数据解析并构建该线最新版本。26.2 基线更新到 `26.2.0.48-beta`。
- AIO 外壳同时提供 FML 1 的 `META-INF/mods.toml` 与现代 FML 的
  `META-INF/neoforge.mods.toml`，并分别使用对应 dependency schema。独立包则按构建前端只保留
  一份元数据，避免 NeoForge 1.20.6 的 FML 3 误读 FML 4 描述文件而拒绝加载。
- 根据 Minecraft 目标分别使用 Java 17、21 或 25；公共运行时保持 Java 17 ABI，版本 Adapter
  可使用目标游戏要求的 Java 版本。
- Fabric 与 NeoForge 发布物都区分完整 standalone 与内部 slim adapter；NeoForge slim adapter
  经过精确 class 重定位后只用于 AIO JarJar 组装。
  构建会检查 Mod ID、Mixin、ServiceLoader、嵌套依赖、字节码版本、目标元数据和 Adapter 哈希。
- Fabric 的公开 Mod ID 仍为 `team-view-relay`；NeoForge 因 FML 命名规则使用
  `team_view_relay`，配置文件名和协议身份不变。

### 集成插件与地图联动

- 新增 Lua 集成插件运行时和稳定能力注册表。插件可提供远程玩家投影、共享路标或战局地图数据源，
  并明确报告可用、未安装、不支持、未实现、入口未就绪或加载失败等状态。
- 内置 NodeMC、SimMC、Xaero 和 JourneyMap 插件，并提供可执行的中英双语 Lua Adapter 示例。
  JourneyMap 同时适配旧版 API v1、API v2 及 26.2 的接口变化；Xaero 包含旧版本兼容入口。
- 新增完整的集成插件管理界面：查看能力和诊断信息、修改插件设置、启停、重新扫描、复制内置示例为
  自定义插件，以及卸载、恢复或永久删除自定义插件。
- 战局地图由固定模式改为可选择的数据源。NodeMC 计分板与 SimMC 原生区域数据共用历史对齐、坐标投影、
  差异检测、缓存保留和 keepalive 上传流程。
- JourneyMap/Xaero 的远程玩家和共享路标同步统一由能力接口管理；插件停用、功能关闭、断线或切换世界时
  会清理其创建的原生标记。

### 实体上传、界面与渲染

- 新增实体上传专用设置页，可选择自适应或固定采集周期。自适应模式会在实体数量较大时自动降低
  采集频率，固定模式仍受服务器协商的最小上报周期约束。
- 新增实体类型与自定义名称的精确白名单/黑名单，可在游戏内分页新增和删除规则；规则在采集阶段应用，
  不需要先构造完整实体对象。
- 配置 UI 改为公共页面模型与各版本原生控件宿主，统一网络、显示、颜色、路标、实体上传、抓包和插件页面；
  无效颜色字段会保留旧值，同时允许其余合法字段继续保存。
- HUD 与世界渲染改为公共 Planner 生成不可变帧、Loader Adapter 执行原生绘制，保证 Fabric 与 NeoForge
  共享相同的显示决策。
- 快速报点使用目标版本的原生射线检测，正确处理交互距离、最大标记距离和方块遮挡。

### 性能、稳定性与协议处理

- 重构高频实体上报：客户端线程单次遍历已加载实体并写入可复用的结构化数组，后台单线程完成状态比较、
  patch 生成和 Protobuf 编码，减少对象分配和游戏线程占用。
- Tab 玩家列表改为独立低频快照和增量比较，不再在每次世界状态采集中重复读取；远程玩家、路标和战局地图
  协调器共享一次轻量世界快照。
- 只有确实需要实体定位时才枚举世界实体；上报、HUD、渲染和地图集成的时钟与清理生命周期集中管理。
- 重连预算改为原子扣减，避免并发重连丢失次数或降到零以下；WebSocket 扩展参数的非法数字和越界值现在
  会转换成带原始值与原因的协议错误。
- 精简 WebSocket 帧回调和无意义队列分支，保留统一的传输流量统计；修正配置逐字段保存、空值处理及
  legacy Gson record 反序列化。

### 架构、构建与质量保障

- 引入 Loader 中立的 Common Adapter SDK、`ClientAdapterBundle`、`ServiceLoader` 启动层和 Adapter TCK。
  网络、配置、同步、HUD、渲染和战局地图业务只保留一份，各版本仅转换原生 Minecraft/Loader API。
- Fabric 与 NeoForge 均采用 `shared -> compat -> version` 三级源码解析。现代 ModDev 和 legacy UserDev
  共同消费唯一的 `neoforge-adapter` 源码树，相同 API 实现可跨构建边界复用，具体版本目录只保留必要覆盖。
- 新增 `source-plan`，可追踪每个目标的有效文件来自 shared、哪个 compat 层或 version override；
  compat 层发生同路径冲突、空层、冗余覆盖或目标遗漏时直接构建失败。
- 所有 NeoForge 构建统一使用根 Gradle 9.5.1 wrapper。Minecraft 1.20.x 的 UserDev 与 1.21+
  的 ModDevGradle 保持独立工程边界，但不再维护第二套 Gradle wrapper。
- `task build` 现在会按 CPU、cgroup 和可用内存并行构建各版本 adapter 与 Fabric runtime；
  每个目标使用独立 Gradle project cache，`JOBS=1` 可恢复串行排障。
- Minecraft 目标、精确 Fabric runtime、依赖版本、Java 要求和发布文件名集中到统一清单；Taskfile、Gradle、
  GitHub Actions、CodeQL 与 Qodana 使用同一套目标信息和本地构建准备 Action。
- 新增公共业务、Adapter TCK、插件、实体管线、配置 UI、战局地图、协议、传输、HUD、渲染以及目标清单测试；
  完整矩阵会逐个编译 31 个 Fabric 正式 runtime 和所有 NeoForge 目标。

### 主要修复

- 修复 Fabric 1.18.2 的渲染矩阵/API 差异，以及跨版本报点距离与遮挡判断。
- 修复 Fabric 1.21.1、26.1、26.1.1 和 26.2 等 JourneyMap 入口注解、Waypoint API 与构建检查问题。
- 修复 SimMC 战争地图区域读取、位置历史与投影问题，以及 Lua/外部插件能力注册、设置持久化和生命周期清理。
- 修复插件使用旧 Gson 时对 Java record 的反序列化兼容问题。
- 修复多个配置解析、空值、并发状态、无界队列语义和包目录问题，并清理未使用缓存及重复版本源码。
- 修复多版本 Fabric/NeoForge 构建、All-in-One 打包、Qodana 项目模型与完整兼容矩阵 CI 问题。
- NeoForge Mixin 的 Java compatibility level 现在由目标工具链生成；legacy UserDev 的严格 Adapter TCK
  使用与 standalone 相同的扁平公共运行时，避免开发运行漏载 bootstrap 或 JarJar 类。
- 为所有 standalone 和 All-in-One 外壳补充跨版本 `pack.mcmeta`，修复 NeoForge 1.20.2 将
  `mod:team_view_relay` 判定为无效 ResourcePack 的启动告警。
- NeoForge 的 `pack.mcmeta` 同时声明旧版 `supported_formats` 与新版 `min_format` / `max_format`，
  避免 Minecraft 1.21.11 和 26.2 在每次资源仓库刷新时重复打印元数据解析异常。
- 将 NeoForge 元数据引用的 Mod 图标放到 Jar 根目录，修复 1.20.2 在 Mod 列表中选择
  TeamViewRelay 时因旧版路径校验不接受 `assets/teamviewer/icon.ico` 而崩溃。

### 升级说明

- 网络协议仍为 `0.6.2`，最低兼容协议仍为 `0.6.1`。本次只发布 Minecraft Mod；后端、网页脚本和
  协议仓库不需要跟随升级。
- Fabric 玩家应在一个匹配版本的独立版和 Fabric All-in-One 之间二选一；NeoForge 玩家应在对应独立版
  和实验性 NeoForge All-in-One 之间二选一。两种 Loader 都不能同时安装 standalone 与 AIO，且不存在
  跨 Loader 通用 Jar。
- 旧配置仍从 `config/team-view-relay.json` 读取；原战局地图模式会回落到默认 NodeMC 数据源，之后可在
  配置页选择实际可用的插件数据源。
- 自定义插件位于 `config/team-view-relay/plugins/`；内置插件只读，只能停用或复制为自定义插件。

## v0.4.14-proto0.6.2

- 上一个公开版本。后续完整提交历史可通过 Git 标签比较查看。
