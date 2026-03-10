# Minecraft TeamViewer

用于 Minecraft 团队协同作战的“视野与报点共享”系统，当前以 **1.21.8 Fabric** 为主版本进行开发和验证。

本仓库包含三部分：

- `minecraft-mod/`：客户端 Fabric Mod（玩家框线渲染、共享报点、房间隔离、与 Xaero 联动）
- `backend-server/`：Python FastAPI WebSocket 服务端（聚合并广播玩家/实体/路标数据）
- `tampermonkey-script/`：管理端油猴脚本工程（接入 `adminws`，可进行战术标注等）

## 1. 核心功能

- 团队玩家信息共享：同步玩家位置、实体信息、共享路标
- 客户端 ESP：远程玩家方框 + 追踪线 + 敌友颜色映射
- 快速报点：按键/中键双击一键报点，支持中键取消、超时清理、数量上限
- Xaero 联动（可选）：
	- 与 Xaero Minimap 双向同步共享路标
	- 与 Xaero World Map 同步远程玩家追踪
- 房间隔离：通过 `roomCode` 分房，互不干扰
- 管理端通道：`/adminws` 实时订阅状态，支持战术路标下发

## 2. 版本与环境

- Minecraft：`1.21.8`
- Fabric Loader：`0.17.2`
- Fabric API：`0.131.0+1.21.8`
- Java：`21`（mod 构建目标）
- Python：`>=3.12`（后端）
- Node.js + pnpm（油猴脚本构建）

## 3. 快速开始（完整链路）

### 3.1 启动后端服务

后端默认监听 `0.0.0.0:8765`。

```bash
cd backend-server/src
uv run main.py
```

可选健康检查：

```bash
curl http://127.0.0.1:8765/health
```

主要端点：

- 玩家客户端：`ws://127.0.0.1:8765/playeresp`
- 管理端：`ws://127.0.0.1:8765/adminws`
- 快照调试：`http://127.0.0.1:8765/snapshot`

### 3.2 构建或获取 Mod

```bash
cd minecraft-mod
chmod +x ./gradlew
./gradlew build
```

输出 jar 位于 `minecraft-mod/build/libs/`（`Taskfile.yml` 也支持一键打包到 `build-artifacts/`）。

### 3.3 安装 Mod（客户端）

至少安装：

- Fabric Loader
- Fabric API
- 本项目 Mod jar

推荐安装（可选）：

- Mod Menu（用于在 Mod 列表中直接打开配置页）
- Xaero Minimap（共享路标联动）
- Xaero World Map（远程玩家追踪联动）

### 3.4 游戏内首次配置

1. 进入游戏后按 `O` 打开配置页（默认快捷键）。
2. 将服务器地址改为：`ws://127.0.0.1:8765/playeresp`。
3. 选择房间号（默认 `default`，同房间互相可见）。
4. 点击“保存服务器设置”，再点击“连接”。

> 注意：当前配置默认值是 `ws://localhost:8080/playeresp`，若后端按默认端口 `8765` 启动，需要手动改地址。

## 4. Mod 介绍与使用

### 4.1 基本操作

- `O`：打开配置面板（默认已绑定）
- 连接开关快捷键：默认未绑定，请在控制设置中手动绑定
- 快速报点快捷键：默认未绑定，请在控制设置中手动绑定

只有在“ESP 已启用且网络已连接”时，报点与同步会生效。

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

配置文件为 `config/multipleplayeresp.json`（Fabric 标准配置目录）。

## 5. 管理端脚本（Tampermonkey）

开发与构建：

```bash
cd tampermonkey-script
pnpm install
pnpm build
```

构建产物在 `tampermonkey-script/dist/*.user.js`，导入 Tampermonkey 即可。

默认管理端 WS：`ws://127.0.0.1:8765/adminws`。

## 6. 常见问题

- 连接失败：优先检查 Mod 配置的 `Server URL` 是否与后端端口一致（常见是 8080/8765 不一致）
- 看不到队友：确认双方 `roomCode` 相同，且都已连接成功
- 报点无效：需先启用 ESP + 建立连接；若使用按键报点，请先手动绑定快捷键
- Xaero 功能不生效：确认已安装对应 Xaero 模组（`xaerominimap` / `xaeroworldmap`）

## 7. 协议与设计文档

- `docs/PLAYER_ESP_NETWORK_PROTOCOL.md`：网络协议与消息结构（0.4.x）
- `docs/RENDER_MODULE_MIGRATION.md`：渲染模块迁移记录