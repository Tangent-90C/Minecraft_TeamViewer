local CAPABILITY_ID = "tab-label-player-relations"
local MAX_MANUAL_TAGS = 12
local MAX_TOWN_TAGS = 128
local MAX_MEMBER_NAMES = 512

local town_friendly = {}
local town_enemy = {}
local member_friendly = {}
local local_town = nil
local collected_at_millis = nil
local pending_town = nil
local pending_friendly = {}
local pending_enemy = {}
local pending_members = {}
local pending_is_local = false
local clear_automatic_relations = nil

tv.configure_setting({
  key = "relation_source_mode",
  detail = "选择自动导入与手动标签的采用关系；默认仅采用 /town 或 /t 的自动识别结果。"
})
tv.configure_setting({
  key = "friendly_tags",
  detail = "启用后手动输入 /town 或 /t 导入关系；多个标签支持逗号、分号或空格分隔，每侧最多 12 项。"
})
tv.configure_setting({
  key = "enemy_tags",
  detail = "手动敌对标签支持逗号、分号或空格分隔，每侧最多 12 项。"
})

local function trim(value)
  return tostring(value or ""):match("^%s*(.-)%s*$")
end

local function strip_format(value)
  return tostring(value or ""):gsub("§[0-9A-FK-ORa-fk-or]", "")
end

local function normalized(value)
  return trim(strip_format(value)):lower()
end

local function set_size(values)
  local count = 0
  for _ in pairs(values) do count = count + 1 end
  return count
end

local function add_key(destination, value, maximum)
  local key = normalized(value)
  if key == "" or key == "无" or destination[key] ~= nil then return false end
  if set_size(destination) >= maximum then return false end
  destination[key] = true
  return true
end

local function parse_manual_tags(raw, maximum)
  local compact = tostring(raw or ""):gsub("，", " "):gsub("；", " ")
  local result = {}
  for tag in compact:gmatch("[^,%s;]+") do
    tag = trim(tag)
    if tag ~= "" then
      result[#result + 1] = tag
      if #result >= maximum then break end
    end
  end
  return result
end

local function add_list(destination, raw, maximum)
  local compact = strip_format(raw):gsub("，", ","):gsub("；", ";")
  for value in compact:gmatch("[^,;]+") do
    add_key(destination, value, maximum)
  end
end

local function contains_any(text, tags)
  for _, tag in ipairs(tags) do
    if string.find(text, tag, 1, true) ~= nil then return true end
  end
  return false
end

local function same_set(first, second)
  for tag in pairs(first) do
    if not second[tag] then return false end
  end
  for tag in pairs(second) do
    if not first[tag] then return false end
  end
  return true
end

local function copy_set(source)
  local result = {}
  for tag in pairs(source) do result[tag] = true end
  return result
end

local function sorted_values(source, exclude)
  local values = {}
  for value in pairs(source) do
    if value ~= exclude then values[#values + 1] = value end
  end
  table.sort(values)
  return values
end

local function summarized(values, unit)
  local visible = {}
  local limit = math.min(#values, 128)
  for index = 1, limit do visible[index] = values[index] end
  local suffix = #values > limit and ("; +" .. (#values - limit)) or ""
  return #values .. " " .. unit .. ": " .. table.concat(visible, ", ") .. suffix
end

local function has_automatic_state()
  return collected_at_millis ~= nil or local_town ~= nil
      or next(town_friendly) ~= nil or next(town_enemy) ~= nil or next(member_friendly) ~= nil
end

local function restore_set(value, maximum)
  local result = {}
  if type(value) ~= "table" then return result end
  for key, present in pairs(value) do
    if present then add_key(result, key, maximum) end
  end
  return result
end

local function save_automatic_state()
  tv.set_persistent_state({
    schema = 1,
    local_town = local_town,
    town_friendly = town_friendly,
    town_enemy = town_enemy,
    member_friendly = member_friendly,
    collected_at_millis = collected_at_millis
  })
end

local function restore_automatic_state()
  local state = tv.get_persistent_state()
  if type(state) ~= "table" or tonumber(state.schema) ~= 1 then return end
  town_friendly = restore_set(state.town_friendly, MAX_TOWN_TAGS)
  town_enemy = restore_set(state.town_enemy, MAX_TOWN_TAGS)
  member_friendly = restore_set(state.member_friendly, MAX_MEMBER_NAMES)
  local restored_town = normalized(state.local_town)
  local_town = restored_town ~= "" and restored_town or nil
  local restored_time = tonumber(state.collected_at_millis)
  collected_at_millis = restored_time ~= nil and restored_time > 0 and restored_time or nil
end

local function publish_runtime_actions()
  local enabled = has_automatic_state() or pending_town ~= nil
  tv.set_runtime_actions({
    {
      id = "clear_automatic_relations",
      label = "清空自动识别",
      tooltip = "清除自动导入的城镇、成员关系和采集时间；不会修改手动标签或关系采用策略。",
      enabled = enabled,
      danger = true,
      confirmation = "确定清空当前自动识别结果吗？手动友军/敌军标签和关系采用策略会保留。",
      callback = function() return clear_automatic_relations() end
    }
  })
end

local function publish_runtime_state(status)
  local friendly_towns = sorted_values(town_friendly, local_town)
  local enemy_towns = sorted_values(town_enemy)
  local members = sorted_values(member_friendly)
  tv.set_runtime_state({
    {key = "source.status", label = "导入状态", value = status or "未导入"},
    {key = "source.local_town", label = "本城", value = local_town or "未识别"},
    {key = "source.friendly_towns", label = "友城", value = summarized(friendly_towns, "个")},
    {key = "source.enemy_towns", label = "敌城", value = summarized(enemy_towns, "个")},
    {key = "source.members", label = "成员来源", value = summarized(members, "人")},
    {
      key = "source.collected_at",
      label = "自动数据采集",
      value = collected_at_millis ~= nil and "最近一次完整结果" or "未采集",
      observed_at_millis = collected_at_millis
    }
  })
  publish_runtime_actions()
end

local function reset_pending()
  pending_town = nil
  pending_friendly = {}
  pending_enemy = {}
  pending_members = {}
  pending_is_local = false
end

clear_automatic_relations = function()
  local changed = has_automatic_state() or pending_town ~= nil
  town_friendly = {}
  town_enemy = {}
  member_friendly = {}
  local_town = nil
  collected_at_millis = nil
  reset_pending()
  tv.clear_persistent_state()
  publish_runtime_state("未导入")
  tv.notify("[TV] 已清空自动识别关系；手动标签和采用策略未修改")
  return changed
end

local function commit_pending()
  if not pending_is_local or pending_town == nil or pending_town == "" then return false end

  local friendly = {}
  add_key(friendly, pending_town, MAX_TOWN_TAGS)
  for town in pairs(pending_friendly) do add_key(friendly, town, MAX_TOWN_TAGS) end
  local enemy = copy_set(pending_enemy)
  local members = copy_set(pending_members)
  local_town = normalized(pending_town)
  local changed = not same_set(town_friendly, friendly)
      or not same_set(town_enemy, enemy)
      or not same_set(member_friendly, members)
  town_friendly = friendly
  town_enemy = enemy
  member_friendly = members
  collected_at_millis = tv.now_millis()
  save_automatic_state()
  publish_runtime_state("已导入")
  return changed
end

local function town_value(line, label_pattern)
  return trim(line):match("^%-%s*" .. label_pattern .. "%s*[:：]%s*(.-)%s*$")
end

local function is_footer(line)
  return string.find(line, '"/town help"', 1, true) ~= nil
      or string.find(line, '"/t help"', 1, true) ~= nil
end

local function notify_import()
  local allies = set_size(pending_friendly)
  local enemies = set_size(pending_enemy)
  tv.notify("[TV] 已导入 " .. pending_town .. "：友军 " .. set_size(pending_members)
      .. " 人，盟友城 " .. allies .. "，敌城 " .. enemies)
end

local function handle_town_message(message)
  if message.overlay then return false end
  local line = trim(strip_format(message.text))
  if line == "" then return false end

  local header = line:match("^城镇%s+(.+)%s*[:：]%s*$")
  if header ~= nil then
    reset_pending()
    pending_town = trim(header)
    return false
  end
  if pending_town == nil then return false end

  local relation = town_value(line, "关系")
  if relation ~= nil then
    pending_is_local = normalized(relation) == "[你]"
    return false
  end

  local allies = town_value(line, "盟友")
  if allies ~= nil then
    pending_friendly = {}
    add_list(pending_friendly, allies, MAX_TOWN_TAGS)
    return false
  end

  local enemies = town_value(line, "敌对")
  if enemies ~= nil then
    pending_enemy = {}
    add_list(pending_enemy, enemies, MAX_TOWN_TAGS)
    return false
  end

  local wars = town_value(line, "正在交战")
  if wars ~= nil then
    add_list(pending_enemy, wars, MAX_TOWN_TAGS)
    return false
  end

  local leader = town_value(line, "领袖")
  if leader ~= nil then
    add_list(pending_members, leader, MAX_MEMBER_NAMES)
    return false
  end

  local officers = town_value(line, "官员%s*%[%d+%]")
  if officers ~= nil then
    add_list(pending_members, officers, MAX_MEMBER_NAMES)
    return false
  end

  local residents = town_value(line, "居民%s*%[%d+%]")
  if residents ~= nil then
    add_list(pending_members, residents, MAX_MEMBER_NAMES)
    return false
  end

  if is_footer(line) then
  local changed = commit_pending()
    if pending_is_local then notify_import() end
    reset_pending()
    return changed
  end
  return false
end

local function add_bracketed_towns(destination, value)
  for town in tostring(value or ""):gmatch("%[([^%]]+)%]") do
    local key = normalized(town)
    if key ~= "" then destination[key] = true end
  end
end

local function town_relation(candidates)
  for town in pairs(candidates) do
    if town_friendly[town] then return "FRIENDLY" end
  end
  for town in pairs(candidates) do
    if town_enemy[town] then return "ENEMY" end
  end
  return nil
end

local function visible_town_relation(prefix_text, prefix_colored)
  local towns = {}
  add_bracketed_towns(towns, prefix_text)
  add_bracketed_towns(towns, prefix_colored)
  return town_relation(towns)
end

local function team_id_relation(team_id)
  local towns = {}
  add_bracketed_towns(towns, team_id)
  local direct = normalized(team_id)
  if direct ~= "" then towns[direct] = true end
  return town_relation(towns)
end

local function manual_relation(player, friendly_tags, enemy_tags)
  local name = tostring(player.name or "")
  local prefix_text = tostring(player.prefixText or "")
  local prefix_colored = tostring(player.prefixColored or "")
  local visible = strip_format(prefix_text) .. " " .. strip_format(prefix_colored)
  local full_name = visible .. " " .. name

  if contains_any(full_name, friendly_tags) then return "FRIENDLY" end
  if contains_any(full_name, enemy_tags) then return "ENEMY" end
  return nil
end

local function automatic_relation(player)
  local name = tostring(player.name or "")
  local prefix_text = tostring(player.prefixText or "")
  local prefix_colored = tostring(player.prefixColored or "")

  if member_friendly[normalized(name)] then return "FRIENDLY" end

  local visible_relation = visible_town_relation(prefix_text, prefix_colored)
  if visible_relation ~= nil then return visible_relation end
  local fallback_relation = team_id_relation(player.teamId)
  if fallback_relation ~= nil then return fallback_relation end

  return nil
end

local function relation_for(player, friendly_tags, enemy_tags, mode)
  local manual = manual_relation(player, friendly_tags, enemy_tags)
  local automatic = automatic_relation(player)
  if mode == "manual_only" then return manual or "NEUTRAL" end
  if mode == "manual_first" then return manual or automatic or "NEUTRAL" end
  if mode == "automatic_first" then return automatic or manual or "NEUTRAL" end
  return automatic or "NEUTRAL"
end

tv.register_player_relation_classifier({
  id = CAPABILITY_ID,
  probe = function()
    return {status = "AVAILABLE", detail = ""}
  end,
  classify = function(players)
    local friendly_tags = parse_manual_tags(settings.friendly_tags, MAX_MANUAL_TAGS)
    local enemy_tags = parse_manual_tags(settings.enemy_tags, MAX_MANUAL_TAGS)
    local mode = tostring(settings.relation_source_mode or "automatic_only")
    local relations = {}
    for _, player in ipairs(players or {}) do
      local player_id = tostring(player.playerId or ""):match("^%s*(.-)%s*$")
      if player_id ~= "" then
        relations[player_id] = relation_for(player, friendly_tags, enemy_tags, mode)
      end
    end
    return relations
  end
})

tv.on_system_chat(handle_town_message)
tv.on_play_session_started(function()
  reset_pending()
  publish_runtime_state(has_automatic_state() and "已恢复" or "未导入")
  return false
end)
tv.on_play_session_ended(function()
  reset_pending()
  publish_runtime_state(has_automatic_state() and "已保存" or "未导入")
  return false
end)

restore_automatic_state()
publish_runtime_state(has_automatic_state() and "已恢复" or "未导入")
