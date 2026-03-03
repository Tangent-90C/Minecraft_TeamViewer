# NodeMC 地图玩家投影（Vue3 + Vite）

这个目录已从单文件油猴脚本重构为 Vue3 + Vite 工程，便于模块化开发与后续功能扩展。

## 目录结构

- `src/index.ts`：主入口（业务编排）
- `src/core/`：核心业务模块
	- `autoMarkSync.ts`：自动标记同步
	- `mapProjection.ts`：地图投影与渲染
- `src/network/`：网络协议与 WS 客户端
	- `networkSchemas.ts`：协议报文模型与构造函数
	- `messageCodec.ts`：报文编解码抽象（默认 MessagePack）
	- `wsClient.ts`：管理端 WS 通道
- `src/ui/`：UI 适配层与样式
	- `settingsUi.ts`：Vue 面板适配
	- `styles.ts`：样式模板
	- `components/OverlaySettingsPanel.vue`：设置面板组件
- `src/config/configTransfer.ts`：配置导入导出
- `src/utils/overlayUtils.ts`：通用归一化与补丁工具
- `src/meta.ts`：集中元信息（userscript / protocol / app）
- `src/constants.ts`：运行时常量与默认配置
- `vite.config.ts`：构建配置（元信息来自 `src/meta.ts`）

## 新增功能/选项建议流程

1. 在 `src/constants.ts` 的 `DEFAULT_CONFIG` 增加默认值。
2. 在 `src/ui/components/OverlaySettingsPanel.vue` 增加对应输入控件并绑定到 `state.form.*`。
3. 在 `src/ui/settingsUi.ts` 的 `fillFormFromConfig` / `readFormCandidate` 增加字段映射。
4. 在 `src/utils/overlayUtils.ts` 的 `sanitizeConfig` 增加该配置的归一化逻辑。

以上流程可保证：UI、存储、配置校验三者同步，降低后续加选项时的遗漏风险。

## 协议与元信息

- 协议报文模型集中在 `src/network/networkSchemas.ts`。
- 传输编解码由 `src/network/messageCodec.ts` 负责，默认使用 MessagePack（二进制帧）。
- 协议版本、userscript 元信息、应用元信息集中在 `src/meta.ts`，避免分散硬编码。

## 配置导入/导出

- 在设置面板基础页新增「导出配置 / 导入配置」按钮。
- 导出文件为 JSON，包含：配置内容、导出时间、兼容版本、程序版本等元信息。
- 导入时会校验兼容版本（按 `LOCAL_PROGRAM_VERSION` 的 `major.minor`），只有同兼容版本允许导入。
- 导入后会统一经过 `sanitizeConfig` 归一化，因此在同兼容版本内即使后续新增字段，也能保持导入可用（缺失字段自动回落默认值，冗余字段自动忽略）。

## 设置交互策略

- 开关类选项（checkbox）改为即时生效，无需点击保存。
- 输入框类选项（文本/数字）采用手动确认：修改后显示“未保存”提示，并在对应分组点击保存后生效。
- 连接设置分组使用“应用连接设置”，点击后会保存并触发重连，避免输入过程频繁重连。

## 开发

```bash
pnpm install
pnpm dev
```

## 打包

```bash
pnpm build
```

打包产物在 `dist/*.user.js`，将该文件导入 Tampermonkey 即可。
