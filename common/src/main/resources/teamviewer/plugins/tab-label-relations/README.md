# Tab Label Player Relations / Tab 标签敌我识别

This built-in plugin classifies players from the cached Minecraft Tab snapshot. It is disabled
by default and never writes marks to the TeamViewRelay room. Enable it in the plugin page, then
after joining a server manually run `/town` or `/t`. The plugin imports the complete reply and
shows an action-bar confirmation when it succeeds. The latest complete result and its original
collection time are stored globally and restored after reconnecting or restarting. They are not
scoped by server and do not expire automatically.
The default `relation_source_mode` is `automatic_only`, so saved manual tags do not affect
classification until a mode that includes manual results is selected.

本内置插件根据 Minecraft Tab 缓存快照在本地识别玩家关系。插件默认关闭，且不会向
TeamViewRelay 房间写入玩家标记。请先在插件页面启用它，进入服务器后手动输入 `/town` 或
`/t`；插件会在收到完整回复后导入，并通过动作栏提示采集成功。重连、切换服务器或城镇关系
变化后，插件会继续恢复并采用最近一次完整结果；这份结果全局保存、不区分服务器且不会自动
过期。插件页会显示采集距今时间，可通过“清空自动识别”删除。
默认关系采用策略为“仅自动识别”，因此已保存的手动标签不会参与分类；切换到包含手动结果的
策略后，手动标签才会生效。

- `relation_source_mode` supports `automatic_only` (default), `manual_only`, `manual_first`, and
  `automatic_first`. The two priority modes use the other source only when the preferred source
  has no result for that player.

- `friendly_tags` and `enemy_tags` accept tags separated by Chinese/ASCII commas,
  semicolons, or whitespace. Each side uses at most 12 tags.
- Example: `friendly_tags = 极乐净土,饶州`, `enemy_tags = 星辉;其他敌对城镇`.
- A `/town` or `/t` result is committed only after `关系: [你]` and the final `/town help` line:
  your town and `盟友` are friendly, while `敌对` and `正在交战` are enemies. `领袖`, `官员` and
  `居民` are also imported as exact friendly player names. Running `/town <other town>` does not
  overwrite the previous result.
- Dynamic town relations first match exact bracketed labels in the visible Tab prefix, then the
  internal scoreboard team ID as a fallback. The plugin keeps at most 128 friendly and 128 enemy
  towns, plus 512 member names. Manual tags retain the full-label/full-name substring fallback.
- Within manual tags, friendly wins when both sides match. Visible unmatched players are neutral.
- The plugin page exposes a confirmed clear action. It removes only automatic town/member data,
  the pending parser and collection time; manual tags and `relation_source_mode` remain unchanged.
- `relation_source_mode` 支持“仅自动识别”（默认）、“仅手动标签”、“手动优先，自动补全”和
  “自动优先，手动补全”。两种优先模式只在首选来源没有识别结果时采用另一来源。
- `friendly_tags` 与 `enemy_tags` 支持中英文逗号、分号或空白分隔，每侧最多 12 项。
- 示例：`friendly_tags = 极乐净土,饶州`，`enemy_tags = 星辉;其他敌对城镇`。
- 仅在结果中出现 `关系: [你]` 并收到末尾 `/town help` 行后，才一次性采纳 `/town` 或
  `/t` 信息：本城和 `盟友` 为友军，`敌对` 与 `正在交战` 为敌军；`领袖`、`官员`、`居民`
  名单也会作为精确友军导入。查询其他城镇不会覆盖当前关系。
- 动态城镇关系优先精确匹配 Tab 可见前缀中的方括号标签，计分板内部队伍 ID 仅作为后备；
  动态友军、敌军各最多 128 个城镇，成员最多 512 人；手动标签仍保留完整标签和玩家名的
  子串兼容匹配。
- 在手动标签内部，同时命中友军与敌军规则时友军优先。
- 当前 Tab 中未命中的玩家会被明确判为中立。
- 插件页的“清空自动识别”需要二次确认，只清除自动城镇/成员数据、待完成解析和采集时间；
  手动友军/敌军标签及 `relation_source_mode` 均保留。
