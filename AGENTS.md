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
`common/src/main/java` 是唯一业务实现，Minecraft/Fabric 版本目录只能实现 Common Adapter SDK。

### Source boundaries

- `common/src/main/java`：协议编排、连接生命周期、同步策略、状态机、配置读写、七页配置页面模型、
  战局地图解析与投影、地图差异计算、世界渲染帧和 HUD 帧计算。
- `fabric/src/shared/java`：确认在所有目标版本中稳定的 Fabric Loader/Fabric API 胶水，以及不直接依赖
  Minecraft 类的可选模组反射端口。
- `fabric-bootstrap/src/main/java`：Java 17 共享 Fabric Loader 入口、Factory 发现、`ClientApplication` 启动和
  Adapter TCK；不得依赖 Minecraft 或 Fabric API 类型。
- `fabric/src/version/<adapter>/java`：具体 Minecraft API 转换、Fabric 事件注册、原生 Screen 控件、
  渲染命令执行、Scoreboard/Mixin 和可选地图模组原生 CRUD。
- `universal`：只负责组装和校验 All-in-One，不得实现业务或重新编译 adapter。
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

1. 在 `gradle/minecraft-versions.properties` 增加目标版本与 adapter 版本映射。
2. 以 `fabric/src/version/1.21.8` 为完整参考，逐项实现 `docs/adapter-sdk.md` 列出的全部端口。
3. 如果 Minecraft API 变化需要新的能力，先扩充 SDK 的强类型 snapshot/command；不要把原生类型泄漏进 common。
4. 同步实现 Scoreboard Mixin、配置 Screen 宿主、世界/HUD sink、JourneyMap/Xaero 端口和战局地图桥。
5. 为 common 业务补假适配器测试，并更新功能矩阵/完整性守卫。缺失能力必须构建失败，不允许生成缩水 Jar。
6. 执行 `task build`，再分别启动各目标客户端进行游戏内烟测。

### Required verification

- `verifyPlatformBoundary`：common 不得依赖平台 API。
- `verifyVersionAdapterBoundary`：版本层不得越过 SDK 调用业务实现。
- `verifyAdapterSdkCompleteness`：每版必须提供完整端口和功能声明。
- `compileVersionAdapterAgainstSdk`：在移除 common-runtime 的类路径下重新编译版本 adapter。
- `verifyNoCommonClassShadowing`：Fabric 外层不得包含与 common 同名的旧类或重复类。
- `verifyAdapterArtifact` / `verifyStandaloneArtifact`：slim 不得夹带 runtime，独立 Jar 必须完整。
- `verifyUniversalJar`：通用包必须包含 manifest 的全部 adapter、正确哈希和唯一共享依赖。
- 不得通过跳过上述任务、删除失败检查或使用旧构建目录来获得“成功”产物。

默认交付命令是 `task build`，它应构建并收集所有受支持 Minecraft 版本；单版本调试使用
`task build-1.21.8`、`task build-26.1.2` 或 `task build-26.2`。
正式产物只有各版本 standalone 和 `TeamViewRelay-Fabric-all-<mod_version>.jar`；
`build/adapter-artifacts` 不得发布。
