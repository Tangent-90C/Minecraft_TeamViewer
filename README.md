# TeamViewRelay Mod

TeamViewRelay 的 Minecraft 客户端 Mod，用于在游戏内共享队友视野、实体、战术报点和共享路标。
当前发布版本为 `v0.8.7-proto0.7.1`。实际验证的运行范围如下：

| 安装产物 | Loader | 可运行的 Minecraft 版本 |
| --- | --- | --- |
| Fabric All-in-One | Fabric | `1.18`–`26.2` 之间的全部 31 个正式版本 |
| Fabric 独立版 | Fabric | 同上，由 17 个按 API 家族划分的 Jar 覆盖 |
| NeoForge All-in-One（实验性） | NeoForge | `1.20.2`–`26.2` 之间的全部 21 个正式版本 |
| NeoForge 独立版 | NeoForge | 同上，由 19 个针对实际 Minecraft/NeoForge 组合编译的 Jar 覆盖 |

Minecraft `1.20.3`、`1.20.5`、`1.21.2`、`1.21.6`、`1.21.7`、`1.21.9` 和 `26.2`
只有 beta NeoForge，相关独立版属于实验性目标。构建时分别固定到已验证的最低 Loader，玩家可以更新
同一 Minecraft 版本线内的 NeoForge；若新版 beta 出现兼容问题，请先回退到清单中的最低版本并附日志反馈。
Minecraft `1.20.1` 不作为 NeoForge 目标，计划由后续独立 Forge 版本支持。

`26.2` 存在客户端 UI、相机和 JourneyMap API 变更，因此使用专用产物；不要把
`26.1.2` 产物强行放到 `26.2`。

Fabric 和 NeoForge 玩家都可选择对应 Loader 的 All-in-One，或选择文件名匹配当前 Minecraft
版本的独立版；两者不能同时安装。NeoForge All-in-One 首个版本属于实验性产物，遇到整合包或旧 FML
兼容问题时应换回对应独立版。完整版本变更见 [`CHANGELOG.md`](CHANGELOG.md)。

整套系统通常由以下组件配合使用：

- [Minecraft-TeamViewer-Backend](https://github.com/MC-TeamViewer/Minecraft-TeamViewer-Backend)：负责房间广播、状态聚合和网页地图通道
- [Minecraft-TeamViewer-Web-Script](https://github.com/MC-TeamViewer/Minecraft-TeamViewer-Web-Script)：把后端状态投影到 squaremap 网页地图
- [map-nodemc-plugin-blocker](https://github.com/MC-TeamViewer/map-nodemc-plugin-blocker)：可选的 NodeMC 页面屏蔽脚本，与本 Mod 无依赖关系

## 项目简介

这个 Mod 面向“团队协同作战”场景，核心能力包括：

- 共享队友位置、实体信息、战局区块和共享路标
- 可选显示权威外部源记录的离线玩家最后位置与本地时间；启用“Tab 标签敌我识别”后，世界方框、追踪线、JourneyMap 与 Xaero Minimap 也会按敌友关系着色
- 快速报点，支持按键触发、中键双击、取消、超时清理、数量上限
- 房间号（`roomCode`）隔离，不同房间互不干扰
- 可选联动 Xaero Minimap、Xaero World Map、JourneyMap

## 适用场景 / 与其他项目关系

- 只安装本 Mod + 后端，即可在游戏内共享团队信息。
- 再配合网页地图脚本，可以把同一房间的状态同步到 squaremap 页面。
- Xaero / JourneyMap 是可选增强，不是运行前置。

推荐搭配关系：

- 后端：负责 `roomCode` 分房、广播和状态快照
- 网页地图脚本：负责网页端地图投影
- Xaero / JourneyMap：负责客户端内的小地图或大地图联动

## 快速开始

1. 安装对应版本的 Fabric Loader + Fabric API，或 NeoForge，以及 Loader 匹配的本 Mod。
2. 启动后按 `O` 打开配置页。
3. 把 `Server URL` 改成你的后端地址，例如 `ws://127.0.0.1:8765/mc-client`。
4. 设置同一房间号（`roomCode`），点击保存并连接。
5. 进入同房间后，验证是否能看到队友、报点或共享路标。

### 将个人城镇敌友关系导入 Web

1. 在 Mod 的插件页启用“Tab 标签敌我识别”，进入服务器后运行 `/town` 或 `/t`。
2. 等待插件显示完整采集结果，然后点击“复制关系档案到 Web”。
3. 回到网页地图设置的“敌友关系”页，从剪贴板导入并确认预览。

关系档案包含本城、友城、敌对/交战城镇、友方成员和采集时间，只通过本机系统剪贴板流转，
不会发送到 TeamViewRelay 房间。网页端保存、优先级和覆盖规则以 Web 脚本页面说明为准。

## 安装 / 运行

1. 根据 Minecraft 版本选择 Fabric 或 NeoForge。
2. Fabric 安装对应 Minecraft 版本的 Fabric API，再选择 All-in-One 或一个匹配的独立版。
3. NeoForge 可选择实验性的 All-in-One，或选择文件名与 Minecraft 版本匹配的独立版；上述 beta 目标同样为实验性。
4. Java 版本必须与 Minecraft 运行要求一致：

| Minecraft | Java |
| --- | --- |
| `1.18`–`1.20.4` | Java 17 |
| `1.20.5`–`1.21.11` | Java 21 |
| `26.1`–`26.2` | Java 25 |

Fabric Loader、Fabric API 和 NeoForge 的精确下限由
[`gradle/minecraft-versions.properties`](gradle/minecraft-versions.properties) 锁定，并写入对应
Jar 的 Loader 元数据。不要通过修改依赖元数据把某个独立版强行用于文件名范围之外的版本。
`neoforge_version` 是可复现的构建 pin，`neoforge_version_range` 是玩家可用范围，`stability`
只用于标记发布渠道。维护者可用 `latest-neoforge-runtime-matrix` 查询每条 beta 线的最新官方版本。

NeoForge 的 JourneyMap、Xaero、SimMC 原生联动会明确报告 `NOT_IMPLEMENTED`，核心连接、同步、HUD、
世界渲染、配置与 NodeMC 功能不因此缩水。

推荐安装：

- Mod Menu：方便直接打开配置页
- Xaero Minimap：共享路标联动
- Xaero World Map：远程玩家追踪联动
- JourneyMap：共享路标和远程玩家地图显示联动

从源码构建：

```bash
./gradlew build
```

该命令保持原行为，默认构建 Fabric 1.21.8。构建单个其他 Fabric 家族时使用
`fabric_target`；26.x 的 Gradle 本身也必须运行在 Java 25 上：

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew -Pfabric_target=26.1.2 :fabric:build
JAVA_HOME=/path/to/jdk-25 ./gradlew -Pfabric_target=26.2 :fabric:build
```

使用 Task 一键完成全矩阵构建：

```bash
task build
```

该任务会从版本清单读取全部 Fabric 发布家族和精确 Minecraft runtime，构建全部
NeoForge 目标、组装 Fabric 与 NeoForge 两个 All-in-One，最后按同一份规范化清单校验发布文件。
Adapter 构建和精确 Fabric runtime 检查会按 CPU、cgroup 配额和可用内存自动并行；公共 runtime
准备及两个 All-in-One 的组装保持串行。每个 Gradle 任务预算 2 GiB，另外为系统预留 2 GiB，
自动并发最多为 8。可以显式调整并发数，低内存或排障时用 `JOBS=1` 恢复串行：

```bash
task build JOBS=4
task build JOBS=1
```

每个子任务的完整输出保存在 `build/parallel-logs/adapters/` 和
`build/parallel-logs/fabric-runtimes/`；控制台只显示开始、结束、耗时和失败日志末尾。并行目标还使用
各自的 `build/parallel-project-cache/`，防止不同版本的同名 Gradle 任务互相清理输出。
使用 `minecraft_targets.py list-*` 命令查询当前数量和范围，避免文档复制易过期的版本表。

本机至少需要 JDK 21、25；JDK 17 是可选的。Java 17 目标会优先使用精确的 JDK 17，
找不到时可由更高版本 JDK 运行 Gradle，并通过 `--release 17` 保持产物兼容 Java 17。
任务会优先读取 `JAVA17_HOME`、`JAVA21_HOME`、`JAVA25_HOME`，也会自动检查当前
`JAVA_HOME` 和常见系统 JDK 目录。例如：

```bash
JAVA21_HOME=/path/to/jdk-21 \
JAVA25_HOME=/path/to/jdk-25 \
task build
```

若系统已经能自动发现 JDK 21，也可以只把当前环境切到 JDK 25：

```bash
JAVA_HOME=/path/to/jdk-25 task build
```

所需 Gradle JDK 版本由同一份 Minecraft 目标清单生成，CI 也使用该命令安装对应的 JDK：

```bash
python3 scripts/minecraft_targets.py list-gradle-java
python3 scripts/minecraft_targets.py latest-neoforge-runtime-matrix
```

`build-artifacts` 最终包含清单定义的全部可发布 Jar；实际数量由目标清单推导，文件名直接写明
Loader 和 Minecraft 支持范围：

- Fabric 多版本家族：`TeamViewRelay-Fabric-MC1.18-to-1.18.2-<mod_version>.jar`
- Fabric 单版本家族：`TeamViewRelay-Fabric-MC1.20.2-<mod_version>.jar`
- NeoForge 单版本目标：`TeamViewRelay-NeoForge-MC1.20.2-<mod_version>.jar`
- Fabric 全版本包：`TeamViewRelay-Fabric-MC1.18-to-26.2-All-in-One-<mod_version>.jar`
- NeoForge 全版本包：`TeamViewRelay-NeoForge-MC1.20.2-to-26.2-All-in-One-<mod_version>.jar`

同一 Loader 的独立版和 All-in-One 二选一安装，不能同时放入 mods 目录。`build/adapter-artifacts`
和 `build/neoforge-adapter-artifacts` 下的 slim adapter 仅供打包使用，不是玩家可安装产物。

维护者发布版本时应按 [`docs/releasing.md`](docs/releasing.md) 完成版本、测试、校验和、标签和
GitHub Release 检查，不要直接发布单个默认 Gradle 构建产物。

Loader 公开 Mod ID 分别为：Fabric `team-view-relay`、NeoForge `team_view_relay`。NeoForge 使用下划线
是因为 FML 的 Mod ID 语法不允许连字符；配置文件名、协议身份和功能行为不因此改变。

开发调试客户端：

```bash
./gradlew runClient
```

## 配置或使用说明

### 离线玩家最后位置

协议 `0.6.4` 可接收由权威外部数据源维护的离线玩家最后位置。此功能默认关闭，可在配置页单独启用；
世界渲染中的方框和追踪线可分别控制，玩家名称与本地时间标签只在最后位置 512 方块内绘制，以限制
远距离玩家较多时的渲染开销。开启总开关后，已支持的 JourneyMap、Xaero Minimap 和 Xaero World Map
也会使用独立标记显示这些记录。玩家重新上线、功能关闭、断线或切换世界时会清理对应历史标记。

Minecraft 26.1.2 + JourneyMap 6.0.5 的小地图只绘制当前视口内的 Relay 玩家标记；视口外玩家不会
进入 JourneyMap 的离屏标签布局，但仍保留在全屏地图中。在线/离线地图标记、世界信标与 Mod 自身的
玩家方框和追踪线使用独立开关。

连接支持协议 `0.7.0` 的后端且 Tab 历史同步未关闭时，common 会仅按离线玩家 UUID 查询最近一次 Tab
标签，并把实时 Tab（优先）与历史记录交给本地“Tab 标签敌我识别”插件。插件的友军、敌军和中立结果会
着色世界方框、追踪线、标签、JourneyMap 和 Xaero Minimap；Xaero World Map 的稳定 tracker API 只能投影位置。

其他 Mod 可通过 Common SDK API v2 的 `TeamViewRelayApi.lastSeenPlayers()` 读取当前可见的不可变历史快照；
旧协议客户端不会收到该数据范围，原有实时玩家同步不受影响。

### 首次配置

配置文件路径：

- `config/team-view-relay.json`

游戏内首次建议配置：

1. 按 `O` 打开配置界面。
2. 填写后端 `Server URL`。
3. 设置房间号（`roomCode`），默认是 `default`。
4. 点击保存服务器设置，再点击连接。

连接地址说明：

- 代码默认值是 `ws://localhost:8080/mc-client`
- 当前后端默认监听端口是 `8765`
- 所以本地常见实际配置应改为 `ws://127.0.0.1:8765/mc-client`

### 常用操作

- `O`：打开配置面板
- 连接开关快捷键：默认未绑定，需要在控制设置中手动绑定
- 快速报点快捷键：默认未绑定，需要在控制设置中手动绑定

只有在“渲染已启用且网络已连接”时，报点与同步才会生效。

### 显示与联动能力

- 远程玩家方框（Box）
- 追踪线（Tracer）
- 敌我中立颜色映射
- 报点渲染样式切换：`beacon` / `ring` / `pin`
- 可选穿墙显示报点和方框（xray）
- Xaero / JourneyMap 路标与玩家显示联动

### 报点机制

- 支持按键快速报点和中键双击报点
- 可启用中键单击取消准星附近本人报点
- 实体死亡后可自动撤销相关报点
- 每位玩家的快捷报点支持数量上限和超时清理

## 常见问题

### 连接失败

- 优先检查 `Server URL` 是否与后端实际监听端口一致。
- 常见错误是 Mod 仍指向默认的 `8080`，而后端实际跑在 `8765`。

### 看不到队友

- 确认双方 `roomCode` 完全一致。
- 确认双方都已经连接成功，而不是只打开了渲染。

### 报点没有效果

- 先确认当前处于“已连接 + 已启用渲染”状态。
- 如果使用按键报点，先在游戏控制设置里绑定快捷键。

### Xaero 或 JourneyMap 联动不生效

- 确认已经安装对应模组。
- 如果只安装本 Mod，本体共享功能仍可使用，只是不会联动外部地图模组。

## 开发与构建

常用命令：

```bash
./gradlew build
task build-target TARGET=1.21.8
task build-neoforge-target TARGET=1.21.8
task check-fabric-runtime RUNTIME=1.21
./gradlew runClient
```

需要直接调用 Gradle 时，Fabric 使用 `-Pfabric_target=<版本>`，NeoForge 使用
`-Pneoforge_target=<版本>`；Taskfile 会自动选择目标需要的 JDK。

主要代码目录：

- `common-sdk`：平台无关的 Adapter SDK 编译边界（snapshot、事件、UI/HUD/世界渲染命令和插件端口）
- `common/src/main/java`：网络、配置、同步、战局地图、HUD/世界渲染规划等唯一业务实现
- `client-bootstrap/src/main/java`：Java 17 Loader 中立启动编排和 Adapter TCK
- `fabric-bootstrap/src/main/java`：Java 17 Fabric Loader 薄入口
- `fabric/src/shared`：所有 Fabric adapter 共用的默认实现
- `neoforge-adapter/src/shared`：modern ModDev 与 legacy UserDev 共用的 NeoForge 默认实现；
  两个 Gradle 工程只是编译前端，不拥有第二份源码
- `fabric/src/compat` / `neoforge-adapter/src/compat`：按 HUD、网络、渲染、Screen 等真实 API 边界复用的兼容层，
  每层通过 `layer.properties` 声明适用 adapter
- `fabric/src/version/<adapter>` / `neoforge-adapter/src/version/<adapter>`：adapter 身份文件及单版本最终覆盖
- `fabric/src/main/resources`：Fabric 公共资源与语言文件
- `universal`：Fabric All-in-One 元数据、组装和产物守卫
- `neoforge-aio`：Java 17 NeoForge AIO 外壳、目标选择、Mixin 选择与 JarJar 组装
- `adapter-relocator`：按实际 adapter class 精确重定位 NeoForge slim adapter 的构建工具

使用 `python3 scripts/minecraft_targets.py source-plan <目标> [--loader neoforge] [--format json]` 可以查看
每个有效文件来自 shared、compat 还是具体版本。版本代码只能实现 Common Adapter SDK，
不得复制业务逻辑。完整边界与打包约束见
[`docs/adapter-sdk.md`](docs/adapter-sdk.md) 和
[`docs/multi-version-packaging.md`](docs/multi-version-packaging.md)。

## 协议 / 版本兼容

当前版本基线：

- Minecraft 支持范围以 `gradle/minecraft-versions.properties` 为唯一来源；运行
  `python3 scripts/minecraft_targets.py list-fabric`、`list-neoforge` 或 `list-official` 查询
- Mod：`v0.8.7-proto0.7.1`
- 协议版本：`0.7.1`
- 最低兼容协议版本：`0.6.1`

子模块与协议仓库：

- 推荐使用 `git clone --recursive`
- 已有仓库可执行 `git submodule update --init --recursive`
- 当前依赖锁定在 `third_party/TeamViewRelay-Protocol` 的指定 commit，不会自动跟随远端更新

升级协议版本的常规流程：

```bash
git -C third_party/TeamViewRelay-Protocol fetch --tags
git -C third_party/TeamViewRelay-Protocol checkout proto/v0.7.1
git add third_party/TeamViewRelay-Protocol
./gradlew build
```

版本号采用“双版本号”约定，例如当前的 `v0.8.7-proto0.7.1`：

- 前半段是程序版本号，用于表示 Mod 自身功能迭代
- 后半段是网络协议版本号，用于表示可与哪些配套组件互通

只有协议版本兼容的 Mod、后端和网页地图脚本，才能稳定协同工作。
