# TeamViewRelay Mod 发布流程

本文用于发布 Minecraft Mod。后端、网页脚本和协议仓库有各自独立的版本与发布周期；只有对应
代码或协议确实变化时才联动发布。

## 版本号

发布标签和 `mod_version` 使用 `v<Mod 版本>-proto<协议版本>`：

- Mod 仅修复兼容性或缺陷：递增 patch，例如 `v0.5.0` 到 `v0.5.1`。
- Mod 新增向后兼容的功能或明显扩大版本/Loader 支持：递增 minor。
- 出现不兼容的公开配置、扩展 API 或行为变化：在进入 `1.0.0` 前递增 minor，并在变更记录中
  明确迁移要求；`1.0.0` 后按 SemVer 递增 major。
- 网络消息或握手兼容性变化：单独递增 `proto` 版本，并同步评估后端和网页脚本。

只修改 Mod 时，保留当前 `proto` 后缀。本次示例为 `v0.5.0-proto0.6.2`。

## 发布前检查

1. 确认工作区干净、协议子模块指向预期标签，并检查相对上一版本的提交：

   ```bash
   git status --short
   git -C third_party/TeamViewRelay-Protocol describe --tags --exact-match HEAD
   git log --oneline <上一标签>..HEAD
   ```

2. 同步更新以下位置：

   - `gradle.properties` 的 `mod_version`
   - `README.md` 的当前版本与用户安装说明
   - `CHANGELOG.md` 的发布日期、功能、修复和兼容性说明
   - 协议确实升级时，再更新 `network_protocol_version`、最低兼容协议和协议子模块

3. 运行基础测试和完整发布矩阵：

   ```bash
   python3 -m unittest discover -s scripts -p 'test_*.py'
   ./gradlew --no-daemon :common:test :common:legacyGsonTest build
   task build
   python3 scripts/minecraft_targets.py verify-release-set
   ```

   `task build` 必须验证 Fabric 的全部正式 runtime、所有 NeoForge 目标并生成 All-in-One。不要把
   `fabric/build/libs`、`neoforge/build/libs` 或 `build/adapter-artifacts` 中的中间产物作为 Release
   附件。

4. 为最终发布文件生成校验和，并确认条目数与 Jar 数一致：

   ```bash
   sha256sum build-artifacts/*.jar > build-artifacts/SHA256SUMS
   find build-artifacts -maxdepth 1 -name '*.jar' -type f | wc -l
   wc -l build-artifacts/SHA256SUMS
   ```

## GitHub 发布

1. 提交并推送版本与文档修改，等待 push CI、CodeQL 和 Qodana 通过。
2. 完整兼容矩阵必须对应发布提交；如果只在本地运行，应在 Release 说明中记录验证环境。
3. CI 全绿后创建并推送与 `mod_version` 完全一致的 annotated tag：

   ```bash
   release_tag=v0.5.0-proto0.6.2
   git tag -a "$release_tag" -m "TeamViewRelay $release_tag"
   git push origin "$release_tag"
   ```

4. 创建 GitHub Release，上传 `build-artifacts` 中的全部正式 Jar 和 `SHA256SUMS`。Release 说明至少
   包含支持范围、安装选择、Java 要求、协议兼容性和主要变更。
5. 发布后从 GitHub 下载至少一个 Fabric 独立版、一个 NeoForge 独立版和 All-in-One，重新核对
   文件名、SHA-256 与 Loader 元数据。

发布过程中不要上传 slim adapter，也不要发布 `sources`、开发环境或未经过
`verify-release-set` 的 Jar。
