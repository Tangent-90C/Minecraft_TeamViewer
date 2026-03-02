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
