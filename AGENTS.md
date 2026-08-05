# Repository Guidelines

## Protocol Dependency

- 共享协议源来自 `third_party/TeamViewRelay-Protocol`
- 本仓库不能重新创建、复制或手改 `.proto`
- submodule 版本由主仓库 commit 锁定，升级需要显式更新 submodule 指针

## Protocol Upgrade Workflow

1. `git -C third_party/TeamViewRelay-Protocol fetch --tags`
2. `git -C third_party/TeamViewRelay-Protocol checkout proto/vX.Y.Z`
3. `git add third_party/TeamViewRelay-Protocol`
4. `./gradlew build`

如果只是改游戏逻辑、渲染或网络处理，不要顺手升级协议 submodule。
如果协议字段变化影响 `ProtobufMessageCodec` 或生成类使用方式，必须同步调整代码并重新验证构建。

## AI Guidance

- 看到协议相关问题时，优先检查 submodule 是否初始化、是否锁到预期 tag、Gradle 生成是否重新运行。
- 不要在本仓库手工恢复或编辑已删除的 Java 生成文件。
- 不要执行“把 submodule 拉到最新 main”这种不带版本锁定的升级。

## Common Adapter SDK Architecture

修改客户端代码前，必须完整阅读 `docs/adapter-sdk.md`。本项目采用桥接模式，
`common/src/main/java` 是唯一业务实现，各 Loader 的 shared/compat/version 源码只能实现 Common Adapter SDK。

### Source boundaries

- `common/src/main/java`：协议编排、连接生命周期、同步策略、状态机、配置读写、七页配置页面模型、
  战局地图解析与投影、地图差异计算、世界渲染帧和 HUD 帧计算。
- `fabric/src/shared/java`：所有 Fabric adapter 共用的默认实现。
- `fabric/src/compat/<capability>-<variant>`：按真实 Minecraft/Fabric API 边界复用的实现；
  `layer.properties` 声明适用 adapter，同一路径不得由两个已选 compat 层同时提供。
- `fabric-bootstrap/src/main/java`：Java 17 共享 Fabric Loader 入口、Factory 发现、`ClientApplication` 启动和
  Adapter TCK；不得依赖 Minecraft 或 Fabric API 类型。
- `fabric/src/version/<adapter>`：adapter 身份和只适用于该 adapter 的最终覆盖；禁止复制已有
  shared/compat 实现。
- `neoforge-adapter/src/shared|compat|version`：全部 NeoForge 目标唯一的源码树；compat 层可以跨越
  ModDev/UserDev 边界。`neoforge` 与 `neoforge-legacy` 只负责按目标工具链编译，禁止重新建立各自的 `src`。
- `universal`：只负责组装和校验 Fabric All-in-One，不得实现业务或重新编译 adapter。
- `neoforge-aio`：只负责 NeoForge AIO 的 Java 17 选择外壳、Mixin 选择、组装和校验；
  `adapter-relocator` 只能精确重定位 adapter 自有 class，禁止改写 Common SDK 类名。
- 禁止重新创建 `common/src/version/*` 或 `fabric/src/client` 这种不对称版本入口。

### Mandatory bridge rules

1. 每个版本必须通过 `ServiceLoader` 提供唯一的 `ClientAdapterFactory<W,H>`，并构造字段全部非空的
   `ClientAdapterBundle<W,H>`。
2. 版本 factory 只能装配原生适配器；共享 Fabric 启动层负责创建 `ClientApplication` 和运行 TCK。
   版本层不得包含配置判断、计时器、协议组装、缓存、同步或渲染算法。
3. 版本层只允许把原生 Minecraft 状态转换为 SDK snapshot，或把 common command 转换为原生 API 调用。
   版本层不得直接导入 `Config`、`NetworkManager`、协议消息、仓库或协调器实现。
4. common 不得导入 Minecraft、Fabric、JourneyMap、Xaero 或其他游戏模组 API。
5. 可选模组必须报告 `AVAILABLE`、`MOD_NOT_INSTALLED`、`UNSUPPORTED_VERSION` 或 `FAILED`；允许某版本不支持
   某插件，但禁止用 `null` 或虚假的完整声明掩盖状态。模组未安装时必须先装配 SDK no-op 端口，禁止在
   能力探测、tick 或清理路径中加载该模组的 API 类。
6. 业务行为、页面控件、HUD 文本和渲染决策需要变化时，先修改 common 及其测试；版本层只补必要的 API 映射。
7. 所有目标版本必须走同一条 `ClientApplication` / `ClientCoordinator` 执行路径。禁止为了修复单一版本而复制一份业务逻辑。
8. common、common-sdk 和 bootstrap 固定为 Java 17；adapter 使用 manifest 的 `adapter_java_release`。
   低于 Java 17 的目标必须先设计 legacy runtime，禁止强迫旧版玩家使用高版本 JRE 绕过兼容边界。
9. standalone 必须保持完整可安装；slim adapter 只能作为构建中间件。All-in-One 中版本类必须保留在各自
   nested Jar，禁止平铺到根命名空间。

### Adding or changing a Minecraft version

1. 在 `gradle/minecraft-versions.properties` 增加目标；可推导的 Java、artifact 和 build kind 不重复声明。
2. 为每个强制端口选择已有 compat 层；API 未变化时只扩展该层的 `adapters`，发生变化时新增能力变体，
   仅单版本实现放入对应 Loader 的 `src/version/<adapter>`；NeoForge 固定使用 `neoforge-adapter/src`。
3. 如果 Minecraft API 变化需要新的能力，先扩充 SDK 的强类型 snapshot/command；不要把原生类型泄漏进 common。
4. 同步实现 Scoreboard Mixin、配置 Screen 宿主、世界/HUD sink、JourneyMap/Xaero 端口和战局地图桥。
5. 为 common 业务补假适配器测试，并更新功能矩阵/完整性守卫。缺失能力必须构建失败，不允许生成缩水 Jar。
6. 先用 `python3 scripts/minecraft_targets.py source-plan <目标>` 审查有效源码，再执行 `task build`
   并分别启动各目标客户端进行游戏内烟测。
7. NeoForge beta 目标必须分别声明可复现的 `neoforge_version`、同 Minecraft 版本线内的
   `neoforge_version_range` 和 `stability=beta`；发布前查询并验证该线最新官方版本。
   Minecraft 1.20.1 属于未来 Forge 边界，禁止为它向 NeoForge AIO 增加双 FML 入口。

### Required verification

- `verifyPlatformBoundary`：common 不得依赖平台 API。
- `verifyVersionAdapterBoundary`：版本层不得越过 SDK 调用业务实现。
- `verifyAdapterSdkCompleteness`：每版必须提供完整端口和功能声明。
- `compileVersionAdapterAgainstSdk`：在移除 common-runtime 的类路径下重新编译版本 adapter。
- `verifyNoCommonClassShadowing`：Fabric 外层不得包含与 common 同名的旧类或重复类。
- `verifyAdapterArtifact` / `verifyStandaloneArtifact`：slim 不得夹带 runtime，独立 Jar 必须完整。
- `verifyUniversalJar` / `verifyNeoForgeAio`：两个 Loader 的通用包必须包含 manifest 的全部 adapter、
  正确哈希、唯一共享依赖，以及唯一入口、Factory、Mixin 和隔离命名空间。
- 不得通过跳过上述任务、删除失败检查或使用旧构建目录来获得“成功”产物。

默认交付命令是 `task build`，它应构建并收集所有受支持 Minecraft 版本；Fabric、NeoForge 和
精确 Fabric runtime 的单目标调试分别使用 `task build-target TARGET=<目标>`、
`task build-neoforge-target TARGET=<目标>` 和 `task check-fabric-runtime RUNTIME=<版本>`。
完整构建默认通过 `scripts/parallel_build.py` 自动并行 adapter 与精确 runtime 检查，公共 runtime
和 AIO 组装保持串行；使用 `task build JOBS=<数量>` 覆盖并发度，`JOBS=1` 用于复现串行问题。
并行任务日志保存在 `build/parallel-logs/`，不得通过忽略失败任务继续组装发布包。
每个并行目标必须保留独立的 `build/parallel-project-cache/`；不得让不同目标共享 Gradle task history。
正式产物只有各版本 standalone、Fabric AIO 和实验性的 NeoForge AIO；
`build/adapter-artifacts` 与 `build/neoforge-adapter-artifacts` 不得发布。

## Release Hygiene

发布 Mod 前必须阅读 `docs/releasing.md`，同步更新 `gradle.properties`、README 和 CHANGELOG，运行
完整 `task build` 与发布集校验，并为所有正式 Jar 生成 SHA-256。Mod、协议、后端和网页脚本各自
独立递增版本；协议未变化时保留现有 `proto` 后缀，不得为凑齐版本号而更新协议 submodule。
