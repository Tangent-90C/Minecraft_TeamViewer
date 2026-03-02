# NodeMC 地图玩家投影（Vue3 + Vite）

这个目录已从单文件油猴脚本重构为 Vue3 + Vite 工程，便于模块化开发与后续功能扩展。

## 目录结构

- `src/index.ts`：主入口（地图投影 + WS + 业务编排）
- `src/settingsUi.ts`：UI 适配层（对外 API 稳定，内部驱动 Vue 面板）
- `src/ui/OverlaySettingsPanel.vue`：设置面板组件
- `src/constants.ts`：常量与默认配置
- `src/styles.ts`：样式模板
- `vite.config.ts`：油猴脚本元信息与构建配置（`vite-plugin-monkey`）

## 新增功能/选项建议流程

1. 在 `src/constants.ts` 的 `DEFAULT_CONFIG` 增加默认值。
2. 在 `src/ui/OverlaySettingsPanel.vue` 增加对应输入控件并绑定到 `state.form.*`。
3. 在 `src/settingsUi.ts` 的 `fillFormFromConfig` / `readFormCandidate` 增加字段映射。
4. 在 `src/overlayUtils.ts` 的 `sanitizeConfig` 增加该配置的归一化逻辑。

以上流程可保证：UI、存储、配置校验三者同步，降低后续加选项时的遗漏风险。

## 配置导入/导出

- 在设置面板基础页新增「导出配置 / 导入配置」按钮。
- 导出文件为 JSON，包含：配置内容、导出时间、兼容版本、程序版本等元信息。
- 导入时会校验兼容版本（按 `LOCAL_PROGRAM_VERSION` 的 `major.minor`），只有同兼容版本允许导入。
- 导入后会统一经过 `sanitizeConfig` 归一化，因此在同兼容版本内即使后续新增字段，也能保持导入可用（缺失字段自动回落默认值，冗余字段自动忽略）。

## 开发

```bash
npm install
npm run dev
```

## 打包

```bash
npm run build
```

打包产物在 `dist/*.user.js`，将该文件导入 Tampermonkey 即可。
