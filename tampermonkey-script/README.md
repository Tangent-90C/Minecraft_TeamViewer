# NodeMC 地图玩家投影（Vite 重构版）

这个目录已从单文件油猴脚本重构为 Vite 工程，便于模块化开发与构建。

## 目录结构

- `src/index.ts`：主入口（业务逻辑）
- `src/constants.ts`：常量与默认配置
- `src/styles.ts`：样式模板
- `src/panelTemplate.ts`：设置面板模板
- `vite.config.ts`：油猴脚本元信息与构建配置（`vite-plugin-monkey`）

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
