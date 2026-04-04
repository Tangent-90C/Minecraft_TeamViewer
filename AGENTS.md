# Repository Guidelines

## Protocol Dependency

- 共享协议源来自 `third_party/TeamViewRelay-Protocol`
- 本仓库不能重新创建、复制或手改 `.proto`
- submodule 版本由主仓库 commit 锁定，升级需要显式更新 submodule 指针

## Protocol Upgrade Workflow

1. `git -C third_party/TeamViewRelay-Protocol fetch --tags`
2. `git -C third_party/TeamViewRelay-Protocol checkout proto/vX.Y.Z`
3. `git add third_party/TeamViewRelay-Protocol`
4. `./gradlew build`

如果只是改游戏逻辑、渲染或网络处理，不要顺手升级协议 submodule。
如果协议字段变化影响 `ProtobufMessageCodec` 或生成类使用方式，必须同步调整代码并重新验证构建。

## AI Guidance

- 看到协议相关问题时，优先检查 submodule 是否初始化、是否锁到预期 tag、Gradle 生成是否重新运行。
- 不要在本仓库手工恢复或编辑已删除的 Java 生成文件。
- 不要执行“把 submodule 拉到最新 main”这种不带版本锁定的升级。
