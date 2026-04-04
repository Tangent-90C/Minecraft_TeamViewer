# TeamViewRelay

用于 Minecraft 团队协同作战的“视野与报点共享”系统，当前以 **1.21.8 Fabric** 为主版本进行开发和验证。


## 1. 核心功能

- 团队玩家信息共享：同步玩家位置、实体信息、共享路标
- 快速报点：按键/中键双击一键报点，支持中键取消、超时清理、数量上限
- Xaero 联动（可选）：
	- 与 Xaero Minimap 双向同步共享路标
	- 与 Xaero World Map 同步远程玩家追踪
 - journeymap 联动（可选）：
 	- 可共享路标，和远程玩家地图显示 
- 房间隔离：通过 `roomCode` 分房，互不干扰

## 2. 版本与环境

- Minecraft：`1.21.8`

## 2.1 子模块与协议版本

- clone 推荐使用：`git clone --recursive`
- 已有仓库补拉子模块：`git submodule update --init --recursive`
- 当前仓库依赖的是被锁定的协议 submodule commit，不会自动跟随协议仓库远端更新
- 升级协议版本的标准流程：

```bash
git -C third_party/TeamViewRelay-Protocol fetch --tags
git -C third_party/TeamViewRelay-Protocol checkout proto/v0.6.0
git add third_party/TeamViewRelay-Protocol
./gradlew build
```

- GitHub “Download ZIP” 不包含 submodule 内容，不是推荐的开发方式

## 3. 快速开始

### 3.1 安装 Mod（客户端）

至少安装：

- Fabric Loader
- Fabric API
- 本项目 Mod jar

推荐安装（可选）：

- Mod Menu（用于在 Mod 列表中直接打开配置页）
- Xaero Minimap（共享路标联动）
- Xaero World Map（远程玩家追踪联动）
- journeymap （远程玩家追踪联动）

### 3.4 游戏内首次配置

1. 进入游戏后按 `O` 打开配置页（默认快捷键）。
2. 服务器默认地址为：`ws://127.0.0.1:8765/mc-client`（改为自己的）
3. 选择房间号（默认 `default`，同房间互相可见）。
4. 点击“保存服务器设置”，再点击“连接”。


## 4. Mod 介绍与使用

### 4.1 基本操作

- `O`：打开配置面板（默认已绑定）
- 连接开关快捷键：默认未绑定，请在控制设置中手动绑定
- 快速报点快捷键：默认未绑定，请在控制设置中手动绑定

只有在“渲染已启用且网络已连接”时，报点与同步会生效。

### 4.2 显示能力

- 远程玩家方框（Box）
- 追踪线（Tracer）
- 敌我中立颜色映射（friendly / neutral / enemy）
- 报点渲染样式可切换：`beacon` / `ring` / `pin`
- 可选“穿墙显示报点和方框”（xray）

### 4.3 报点机制

- 快捷报点：按“快速报点”按键，或启用“中键双击报点”
- 取消报点：启用后可“中键单击取消准星附近本人报点”
- 自动取消：实体报点在本地确认目标死亡后可自动撤销
- 数量限制：每位玩家快捷报点超过上限时，会自动清理较旧报点
- 超时清理：普通报点与长期报点分别支持独立 TTL

### 4.4 配置入口说明

- 主配置页：服务端 URL、房间号、连接/断开
- 显示设置页：渲染距离、方框/线条开关、追踪线起点、颜色/报点子页
- 网络设置页：上报频率、实体上报开关、共享路标上报、系统代理
- 报点设置页：报点显示、中键交互、长期报点、样式、形状参数

配置文件为 `config/team-view-relay.json`（Fabric 标准配置目录）。

## 6. 常见问题

- 连接失败：优先检查 Mod 配置的 `Server URL` 是否与后端端口一致（常见是 8080/8765 不一致）
- 看不到队友：确认双方 `roomCode` 相同，且都已连接成功
- 报点无效：需先启用 渲染 + 建立连接；若使用按键报点，请先手动绑定快捷键
- Xaero 功能不生效：确认已安装对应 Xaero 模组（`xaerominimap` / `xaeroworldmap`）

版本号说明
该程序采用双版本号体系，例如 v0.2.1-proto0.3.0 ，具体含义如下：

程序版本号为 0.2.1，此版本号主要标识程序自身的功能迭代与更新情况。
网络协议版本号为 0.3.0，该版本号用于界定与其他配套程序进行网络通信时所采用的网络协议版本。
只有各个程序间使用相似甚至完全相同的网络协议版本号，才能实现相互连接并正常协同使用。
