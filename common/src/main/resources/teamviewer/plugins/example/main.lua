--[[
TeamViewRelay Lua Adapter reference / Lua Adapter 标杆示例

This built-in plugin is disabled by default and deliberately does nothing. It shows every
public host API and every adapter contract. Copy it from the plugin manager before editing.
此内置插件默认关闭，所有回调均为空操作。它展示全部宿主 API 与 Adapter 契约；请先在
插件管理页“复制为自定义插件”，再开始修改。
]]

local IDS = {
  remote_player = "teamviewer-example-remote-player",
  shared_waypoint = "teamviewer-example-shared-waypoint",
  battle_map = "teamviewer-example-battle-map"
}

-- Dynamic support probes are called by the registry. Never claim AVAILABLE before the
-- external API and cached reflection handles are actually usable.
-- 注册表会动态调用 probe；外部 API 和反射句柄未就绪前不要声称 AVAILABLE。
local function ready_probe()
  return {status = "AVAILABLE", detail = ""}
end

-- Read-only host APIs / 只读宿主 API。This function is documentation and is never called.
local function inspect_host_api()
  -- Optional dynamic UI state for a manifest-declared setting. Omitting this call keeps it
  -- visible and enabled. / 可选的动态 UI 状态；不调用时默认可见、可编辑。
  tv.configure_setting({key = "enabled_marker", visible = true, enabled = true,
      detail = "Example runtime setting state"})
  -- Up to eight read-only fields shown on the plugin page; these are never persisted.
  tv.set_runtime_state({
    {key = "example.status", label = "Example status", value = "Ready"}
  })
  local loader = environment.loader_id()
  local minecraft = environment.minecraft_version()
  local mod_version = environment.mod_version("replace-with-external-mod-id")
  local installed = mods.is_loaded("replace-with-external-mod-id")
  local entrypoint_object = services.get("replace-with-service-id")
  local minecraft_objects = services.get("minecraft.client_objects")
  if minecraft_objects ~= nil then
    -- Stable mapped-object factories; do not load net.minecraft.* development names from Lua.
    -- 稳定映射对象工厂；不要在 Lua 中直接加载开发环境的 net.minecraft.* 类名。
    local example_pos = minecraft_objects:blockPosition(0, 0, 0)
    local example_dimension = minecraft_objects:dimensionKey("minecraft:overworld")
  end

  local world = snapshots.world()
  local players = snapshots.players()
  local waypoints = snapshots.waypoints()
  local scoreboard = snapshots.scoreboard()
  local tab_players = snapshots.tab_players()
  -- System chat callbacks receive {text, overlay}; return true when a callback changed
  -- relation state and needs the current Tab cache classified again.

  tv.log.info("informational message")
  tv.log.warn("warning message")
  tv.log.error("error message")
  -- tv.notify("local action-bar message") -- returns false when the host has no notifier.
  return loader, minecraft, mod_version, installed, entrypoint_object,
      world, players, waypoints, scoreboard, tab_players
end

-- Java/LuaJ bridge examples. java.type/method/field cache their handles in the host.
-- Java/LuaJ 桥示例；java.type/method/field 的句柄由宿主缓存。
local function reflection_examples()
  local ArrayList = java.type("java.util.ArrayList")
  local parsed_number = java.type("java.lang.Integer"):parseInt("7")
  local size_method = java.method("java.util.ArrayList", "size")
  local max_field = java.field("java.lang.Integer", "MAX_VALUE")
  local list = java["new"](ArrayList)
  local runnable = java.proxy("java.lang.Runnable", {run = function() end})
  return size_method, max_field, list, runnable, parsed_number
end

-- Alternative 1: bind a capability already implemented by Java. This does not implement an
-- external-mod adapter; it only aliases a registered native implementation.
-- 备选 1：绑定已有 Java 能力。这不是外部 Mod Adapter，只是复用已登记的原生实现。
-- tv.use_native_capability("declared-custom-id", "existing-native-capability-id")

-- Alternative 2: when this runtime cannot implement a declared capability, register the reason
-- instead of omitting it. / 无法实现时必须登记原因，不能让能力静默消失。
-- tv.register_unavailable_capability({
--   id = "declared-custom-id", status = "NOT_IMPLEMENTED", detail = "explain why"
-- })

tv.register_remote_player_projection({
  id = IDS.remote_player,
  probe = ready_probe,

  -- players and relations are keyed by UUID. Older callbacks may omit the third argument.
  -- players 与 relations 均以 UUID 为键；旧回调可以省略第三个参数。
  sync = function(players, enabled, relations)
    -- Convert TeamViewRelay remote players into external-mod markers here.
    -- 在这里把 TeamViewRelay 远程玩家转换为外部 Mod 标记。
  end,

  clear = function()
    -- Remove only objects owned by this plugin. / 只清理由本插件创建的对象。
  end
})

tv.register_shared_waypoint_adapter({
  id = IDS.shared_waypoint,
  probe = ready_probe,

  -- Return an array of {nativeId,name,symbol,x,y,z,dimension,color}.
  -- 返回由上述字段组成的数组；不要返回本插件自己创建的远程路标。
  list_local = function()
    return {}
  end,

  -- command exposes waypointId,name,symbol,x,y,z,dimension,color.
  -- command 包含上述统一字段；在这里写入或更新外部 Mod 路标。
  upsert_remote = function(command) end,
  delete_remote = function(waypoint_id) end,
  clear_remote = function() end
})

tv.register_battle_map_source({
  id = IDS.battle_map,
  probe = ready_probe,

  -- Return nil when no observation is available, otherwise return:
  -- {dimension,observedAt,coordinateSpace,mapSize,anchorRow,anchorColumn,
  --  cells={{x,z,symbol,color}, ...}}
  -- 无观测时返回 nil；否则返回上述统一战局地图结构。
  capture = function()
    return nil
  end
})

-- A player-relation classifier receives the common cached Tab snapshot and returns a table
-- keyed by UUID with FRIENDLY, ENEMY or NEUTRAL values. Missing keys mean no decision.
-- 玩家关系分类器接收 common 缓存的 Tab 快照；缺失的 UUID 表示不作决定。
-- tv.register_player_relation_classifier({
--   id = "declared-player-relation-capability",
--   probe = ready_probe,
--   classify = function(tab_players) return {} end
-- })

tv.on_enable(function()
  -- Allocate plugin-owned external objects here. / 在这里初始化插件拥有的外部对象。
end)

tv.on_disable(function()
  -- Final cleanup; adapter clear callbacks run before this hook.
  -- 最终清理；宿主会先执行各 Adapter 的 clear 回调。
end)

tv.on_settings_changed(function(key, value)
  -- settings contains boolean/integer/number/string/enum/color values declared in plugin.json.
  -- settings 表包含清单声明的六种动态设置；value 是已经规范化的新值。
  local current = settings[key]
end)

tv.on_system_chat(function(message)
  local text = message.text
  local is_overlay = message.overlay
  return false
end)

tv.on_play_session_started(function()
  -- Clear any data scoped to one multiplayer server here.
end)

tv.on_play_session_ended(function()
  -- Release session-scoped data here.
end)
