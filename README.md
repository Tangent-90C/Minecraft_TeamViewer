# TeamViewRelay Mod

TeamViewRelay 的 Minecraft 客户端 Mod，用于在游戏内共享队友视野、实体、战术报点和共享路标。
当前实际验证的运行范围如下：

| 安装产物 | Loader | 可运行的 Minecraft 版本 |
| --- | --- | --- |
| `Fabric-1.21.8` | Fabric | `1.21.6`–`1.21.8` |
| `NeoForge-1.21.8` | NeoForge | `1.21.8` |
| `Fabric/NeoForge-26.1.2` | 对应 Loader | `26.1`–`26.1.2` |
| `Fabric/NeoForge-26.2` | 对应 Loader | `26.2` |

`26.2` 存在客户端 UI、相机和 JourneyMap API 变更，因此使用专用产物；不要把
`26.1.2` 产物强行放到 `26.2`。

整套系统通常由以下组件配合使用：

- [Minecraft-TeamViewer-Backend](https://github.com/MC-TeamViewer/Minecraft-TeamViewer-Backend)：负责房间广播、状态聚合和网页地图通道
- [Minecraft-TeamViewer-Web-Script](https://github.com/MC-TeamViewer/Minecraft-TeamViewer-Web-Script)：把后端状态投影到 squaremap 网页地图
- [map-nodemc-plugin-blocker](https://github.com/MC-TeamViewer/map-nodemc-plugin-blocker)：可选的 NodeMC 页面屏蔽脚本，与本 Mod 无依赖关系

## 项目简介

这个 Mod 面向“团队协同作战”场景，核心能力包括：

- 共享队友位置、实体信息、战局区块和共享路标
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

## 安装 / 运行

1.21 系列产物至少需要：

- Fabric：Minecraft `1.21.6`–`1.21.8`
- NeoForge：Minecraft `1.21.8`
- Java `21`
- Fabric Loader `0.17.2`
- 与当前 Minecraft 版本匹配的 Fabric API
- 本项目 Mod Jar

26.1 系列产物至少需要：

- Minecraft `26.1`–`26.1.2`
- Java `25`
- Fabric Loader `0.19.3`
- 与当前 Minecraft 版本匹配的 Fabric API
- 文件名含 `26.1.2` 的本项目 Mod Jar

26.2 产物需要 Minecraft `26.2`、Java `25`、Fabric Loader `0.19.3` 与匹配的 Fabric API，
或 NeoForge `26.2.0.32-beta` 及更新的 26.2 版本。

NeoForge 版本分别要求 NeoForge `21.8.54`（Minecraft 1.21.8）、26.1 系列 NeoForge
（Minecraft 26.1–26.1.2）或 `26.2.0.32-beta` 及更新的 26.2 版本。NeoForge 的
JourneyMap、Xaero、SimMC 原生联动会明确报告 `NOT_IMPLEMENTED`，核心连接、同步、HUD、
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

该命令保持原行为，默认构建 1.21.8。构建 26.x 时，Gradle 本身也必须运行在 Java 25 上：

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew -Pmc_target=26.1.2 build
JAVA_HOME=/path/to/jdk-25 ./gradlew -Pmc_target=26.2 build
```

使用 Task 构建全部七个正式产物（需要对应的两个 JDK）：

```bash
JAVA21_HOME=/path/to/jdk-21 JAVA25_HOME=/path/to/jdk-25 task build
```

`build-artifacts` 最终包含：

- `TeamViewRelay-Fabric-1.21.8-<mod_version>.jar`
- `TeamViewRelay-Fabric-26.1.2-<mod_version>.jar`
- `TeamViewRelay-Fabric-26.2-<mod_version>.jar`
- `TeamViewRelay-Fabric-all-<mod_version>.jar`
- `TeamViewRelay-NeoForge-1.21.8-<mod_version>.jar`
- `TeamViewRelay-NeoForge-26.1.2-<mod_version>.jar`
- `TeamViewRelay-NeoForge-26.2-<mod_version>.jar`

独立版和 All-in-One 二选一安装，不能同时放入 mods 目录。`build/adapter-artifacts` 下的 slim adapter
仅供打包使用，不是玩家可安装产物。

Loader 公开 Mod ID 分别为：Fabric `team-view-relay`、NeoForge `team_view_relay`。NeoForge 使用下划线
是因为 FML 的 Mod ID 语法不允许连字符；配置文件名、协议身份和功能行为不因此改变。

开发调试客户端：

```bash
./gradlew runClient
```

## 配置或使用说明

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
JAVA_HOME=/path/to/jdk-25 ./gradlew -Pmc_target=26.1.2 build
JAVA_HOME=/path/to/jdk-25 ./gradlew -Pmc_target=26.2 build
./gradlew runClient
```

主要代码目录：

- `common-sdk`：平台无关的 Adapter SDK 编译边界（snapshot、事件、UI/HUD/世界渲染命令和插件端口）
- `common/src/main/java`：网络、配置、同步、战局地图、HUD/世界渲染规划等唯一业务实现
- `client-bootstrap/src/main/java`：Java 17 Loader 中立启动编排和 Adapter TCK
- `fabric-bootstrap/src/main/java`：Java 17 Fabric Loader 薄入口
- `fabric/src/shared`：确实跨 Minecraft 版本稳定的 adapter 胶水
- `fabric/src/version/1.21.8`：Minecraft 1.21.6–1.21.8 API 适配
- `fabric/src/version/26.1`：Minecraft 26.1–26.2 API 适配（26.2 使用专用依赖与元数据）
- `neoforge/src/shared`：NeoForge 薄入口及稳定事件总线胶水
- `neoforge/src/version/1.21.8` / `26.1`：NeoForge Minecraft API 适配；后者覆盖 26.1–26.2
- `universal`：All-in-One 元数据、组装和产物守卫

具体版本代码只能实现 Common Adapter SDK，不得复制业务逻辑。完整边界与打包约束见
[`docs/adapter-sdk.md`](docs/adapter-sdk.md) 和
[`docs/multi-version-packaging.md`](docs/multi-version-packaging.md)。
- `fabric/src/main/resources`：Fabric 资源与语言文件

## 协议 / 版本兼容

当前版本基线：

- Minecraft：Fabric `1.21.6`–`1.21.8`、Fabric/NeoForge `26.1`–`26.2`；
  NeoForge 1.21 系列仅 `1.21.8`
- Mod：`v0.4.14-proto0.6.2`
- 协议版本：`0.6.2`
- 最低兼容协议版本：`0.6.1`

子模块与协议仓库：

- 推荐使用 `git clone --recursive`
- 已有仓库可执行 `git submodule update --init --recursive`
- 当前依赖锁定在 `third_party/TeamViewRelay-Protocol` 的指定 commit，不会自动跟随远端更新

升级协议版本的常规流程：

```bash
git -C third_party/TeamViewRelay-Protocol fetch --tags
git -C third_party/TeamViewRelay-Protocol checkout proto/v0.6.1
git add third_party/TeamViewRelay-Protocol
./gradlew build
```

版本号采用“双版本号”约定，例如 `v0.4.12-proto0.6.1`：

- 前半段是程序版本号，用于表示 Mod 自身功能迭代
- 后半段是网络协议版本号，用于表示可与哪些配套组件互通

只有协议版本兼容的 Mod、后端和网页地图脚本，才能稳定协同工作。
