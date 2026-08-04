# Changelog

本文件记录 TeamViewRelay Mod 的用户可见变更。版本号格式为
`v<Mod 版本>-proto<协议版本>`；协议版本未变化时，后端和网页脚本不需要跟随 Mod 一起发布。

## v0.5.0-proto0.6.2 - 2026-08-04

### 新增

- Fabric 覆盖 Minecraft `1.18`–`26.2` 之间的全部 31 个正式版本，提供 17 个独立版和一个
  All-in-One。
- 新增 NeoForge 支持，发布 13 个目标，覆盖选定的 `1.20.2`–`26.2` 版本。
- 引入 Loader 中立的 Adapter SDK、能力注册表和 TCK，使版本适配与业务逻辑保持分离。
- 加入 `shared -> compat -> version` 三级源码解析和 `source-plan`，可按真实 API 边界复用端口实现。

### 调整

- 所有 NeoForge 构建统一使用根 Gradle 9.5.1 wrapper；legacy UserDev 与现代 ModDevGradle 仍保持
  独立工程边界。
- Fabric 独立版、NeoForge 独立版、slim adapter 和 All-in-One 使用同一份目标清单与产物校验规则。
- 外部地图和 SimMC 集成改为稳定能力接口与插件化装配，缺失或不支持的能力会明确报告状态。
- 优化实体采集、Tab 信息、配置保存、网络重连和多版本源码结构，减少运行与维护开销。

### 修复

- 修复 JourneyMap 不同 API 世代的入口注解与兼容性检查。
- 修复 Fabric 1.18.2 渲染、26.1/26.1.1 JourneyMap 入口及多个完整兼容矩阵 CI 问题。
- 修复 Gson 兼容反序列化、SimMC 战争地图和插件对接问题。

### 兼容性

- 网络协议仍为 `0.6.2`，最低兼容协议为 `0.6.1`。
- 本次只发布 Minecraft Mod；后端、网页脚本和协议仓库没有版本变更。
- Fabric 独立版与 All-in-One 不能同时安装；NeoForge 只提供独立版。

## v0.4.14-proto0.6.2

- 上一个公开版本。后续完整提交历史可通过 Git 标签比较查看。
