-- JourneyMap API v2 without per-waypoint presentation controls.
-- 不支持逐路标显示控制的 JourneyMap API v2。

local MOD_ID, JM_MOD_ID = "journeymap", "teamviewer"
local handles, handle_error = nil, nil
local managed_players, managed_last_seen, managed_waypoints = {}, {}, {}
local client_objects = services.get("minecraft.client_objects")
local GROUP_ROLE_KEY = "teamviewer.group.role"
local group_definitions = {
  online = {name = "TeamViewRelay Online Players", tag = "[TV] Online"},
  offline = {name = "TeamViewRelay Offline Players", tag = "[TV] Offline"},
  player_reports = {name = "TeamViewRelay Player Reports", tag = "[TV] Reports"},
  web_reports = {name = "TeamViewRelay Web & Admin Reports", tag = "[TV] Web"},
  other_shared = {name = "TeamViewRelay Other Shared Waypoints", tag = "[TV] Other"}
}
local group_order = {"online", "offline", "player_reports", "web_reports", "other_shared"}
local managed_groups, group_support, group_world_id = {}, nil, nil

local function configure_settings(available)
  tv.configure_setting({key = "show_remote_players", visible = available, enabled = available,
    detail = "Map and world visibility follow JourneyMap's global waypoint settings"})
  tv.configure_setting({key = "show_last_seen_players", visible = available, enabled = available,
    detail = "Map and world visibility follow JourneyMap's global waypoint settings"})
  tv.configure_setting({key = "show_online_map_markers", visible = false, enabled = false})
  tv.configure_setting({key = "show_online_world_beacons", visible = false, enabled = false})
  tv.configure_setting({key = "show_offline_map_markers", visible = false, enabled = false})
  tv.configure_setting({key = "show_offline_world_beacons", visible = false, enabled = false})
  for _, role in ipairs(group_order) do
    for _, suffix in ipairs({"render_world", "rotating_beam", "static_beam", "max_distance"}) do
      tv.configure_setting({key = role .. "_" .. suffix, visible = false, enabled = false,
        detail = "This JourneyMap build uses global waypoint beacon settings"})
    end
  end
end

configure_settings(mods.is_loaded(MOD_ID))

-- 1. Version-specific handles and dynamic service / 版本句柄与动态服务
local function initialize()
  if handles ~= nil or handle_error ~= nil then return handles ~= nil end
  local ok, value = pcall(function()
    return {
      WaypointFactory = java.type("journeymap.api.v2.common.waypoint.WaypointFactory"),
      LastSeenTimeFormatter = java.type("fun.prof_chen.teamviewer.main_code.time.LastSeenTimeFormatter"),
      iterableIterator = java.method("java.lang.Iterable", "iterator"),
      iteratorHasNext = java.method("java.util.Iterator", "hasNext"),
      iteratorNext = java.method("java.util.Iterator", "next")
    }
  end)
  if ok then handles = value else handle_error = tostring(value) end
  return handles ~= nil
end

local function api() return services.get("journeymap.client_api") end
local ensure_groups
local function probe()
  if not mods.is_loaded(MOD_ID) then
    return {status = "MOD_NOT_INSTALLED", detail = "journeymap is not installed"}
  end
  if client_objects == nil then return {status = "FAILED", detail = "minecraft.client_objects service is unavailable"} end
  if not initialize() then
    configure_settings(false)
    return {status = "UNSUPPORTED_VERSION", detail = handle_error}
  end
  if api() == nil then return {status = "ENTRYPOINT_NOT_READY", detail = "JourneyMap IClientAPI is not initialized"} end
  local ready, detail = ensure_groups()
  if not ready and group_support ~= false then return {status = "ENTRYPOINT_NOT_READY", detail = detail} end
  return {status = "AVAILABLE", detail = group_support == false and detail or ""}
end

local function dimension(id)
  return client_objects:dimensionKey(id)
end

local function disable_groups(detail)
  if group_support == false then return end
  group_support, managed_groups = false, {}
  tv.log.warn("JourneyMap waypoint groups are unavailable; using default waypoints: " .. tostring(detail))
end

local function reset_group_scope()
  managed_groups, group_support, group_world_id = {}, nil, nil
end

local function java_values(iterable)
  local result = {}
  local iterator = handles.iterableIterator:invoke(iterable, nil)
  while handles.iteratorHasNext:invoke(iterator, nil) == true do
    table.insert(result, handles.iteratorNext:invoke(iterator, nil))
  end
  return result
end

local function group_guid(group)
  return tostring(group:getGuid())
end

local function group_role(group)
  local ok, value = pcall(function() return group:getCustomData(GROUP_ROLE_KEY) end)
  return ok and value ~= nil and tostring(value) or nil
end

local function same_text(left, right)
  return left ~= nil and right ~= nil and string.lower(tostring(left)) == string.lower(tostring(right))
end

local function select_group(groups, definition, role, used)
  local priorities = {{}, {}, {}}
  for _, group in ipairs(groups) do
    local guid = group_guid(group)
    if not used[guid] and tostring(group:getModId()) == JM_MOD_ID then
      if group_role(group) == role then
        table.insert(priorities[1], group)
      elseif same_text(group:getName(), definition.name) then
        table.insert(priorities[2], group)
      elseif same_text(group:getTag(), definition.tag) then
        table.insert(priorities[3], group)
      end
    end
  end
  for _, candidates in ipairs(priorities) do
    table.sort(candidates, function(left, right) return group_guid(left) < group_guid(right) end)
    if #candidates > 0 then return candidates[1] end
  end
  return nil
end

ensure_groups = function()
  if group_support == false then return false, "waypoint groups are unavailable" end
  local client = api()
  if client == nil or handles == nil then return false, "JourneyMap API is unavailable" end
  local resolved, value, world_id = pcall(function()
    local current_world_id = client:getWorldId()
    if current_world_id == nil or tostring(current_world_id) == "" then
      return nil, nil
    end
    local groups = java_values(client:getAllWaypointGroups())
    if #groups == 0 then return nil, tostring(current_world_id) end
    return groups, tostring(current_world_id)
  end)
  if not resolved then
    disable_groups(value)
    return false, tostring(value)
  end
  local groups = value
  if groups == nil then
    managed_groups = {}
    return false, "JourneyMap waypoint-group store is not ready"
  end
  if group_world_id ~= world_id then
    managed_groups, group_support, group_world_id = {}, nil, world_id
  end
  local current = {}
  for _, group in ipairs(groups) do current[group_guid(group)] = group end
  for role, cached in pairs(managed_groups) do
    managed_groups[role] = current[group_guid(cached)]
  end
  local used = {}
  for _, role in ipairs(group_order) do
    local definition = group_definitions[role]
    local group = managed_groups[role]
    if group == nil then group = select_group(groups, definition, role, used) end
    local created = false
    if group == nil then
      group = handles.WaypointFactory:createWaypointGroup(JM_MOD_ID, definition.name)
      if group == nil then
        disable_groups("JourneyMap did not create " .. definition.name)
        return false, "JourneyMap did not create " .. definition.name
      end
      group:setTag(definition.tag)
      created = true
    end
    local persistent = false
    pcall(function() persistent = group:isPersistent() end)
    local marker_changed = false
    if group_role(group) ~= role then
      marker_changed = pcall(function() group:setCustomData(GROUP_ROLE_KEY, role) end)
    end
    if not persistent then group:setPersistent(true) end
    if created or marker_changed or not persistent then client:addWaypointGroup(group) end
    managed_groups[role] = group
    used[group_guid(group)] = true
    if created then table.insert(groups, group) end
  end
  group_support = true
  return true, ""
end

local function assign_group(object, key)
  if key == nil then return true end
  if not ensure_groups() then return false end
  local group = group_support == true and managed_groups[key] or nil
  if group == nil then return false end
  local assigned, assignment_error = pcall(function() group:addWaypoint(object) end)
  if not assigned then disable_groups(assignment_error) end
  return assigned
end

local function shared_group(command)
  for _, key in ipairs({"waypointKind", "tacticalType", "sourceType"}) do
    local value = command[key]
    if value ~= nil then
      local normalized = string.lower(tostring(value))
      if normalized == "web_map_tactical" or normalized == "admin_tactical" then return "web_reports" end
    end
  end
  local kind = command.waypointKind ~= nil and string.lower(tostring(command.waypointKind)) or ""
  if kind == "quick" or kind == "manual" then return "player_reports" end
  return "other_shared"
end

local function deletion_failed(state, id, detail)
  state.pending_delete = true
  if not state.delete_warned then
    tv.log.warn("JourneyMap retained managed waypoint " .. tostring(id) .. ": " .. tostring(detail))
    state.delete_warned = true
  end
  return false
end

local function has_pending(managed)
  for _, value in pairs(managed) do if value.pending_delete then return true end end
  return false
end

local function remove(managed, id)
  local value = managed[id]
  if value == nil then return true end
  local client = api()
  if client == nil then return deletion_failed(value, id, "JourneyMap API is unavailable") end
  local removed, remove_error = pcall(function() client:removeWaypoint(JM_MOD_ID, value.object) end)
  if not removed then return deletion_failed(value, id, remove_error) end
  local verified, remaining = pcall(function()
    return client:getWaypoint(JM_MOD_ID, tostring(value.object:getGuid())) ~= nil
  end)
  if verified and remaining then return deletion_failed(value, id, "native delete was suppressed") end
  if value.delete_warned then tv.log.info("JourneyMap cleanup recovered for " .. tostring(id)) end
  managed[id] = nil
  return true
end

local function clear(managed)
  local ids = {}; for id, _ in pairs(managed) do table.insert(ids, id) end
  for _, id in ipairs(ids) do remove(managed, id) end
end

-- 2. Common object -> JourneyMap object / common 对象 -> JourneyMap 对象
local function upsert(managed, id, name, x, y, z, dimension_id, color, group_key)
  local client = api(); if client == nil then return end
  local state = managed[id]
  if state ~= nil and state.name ~= name then
    if not remove(managed, id) then return end
    state = nil
  end
  if state == nil then
    local position = client_objects:blockPosition(x, y, z)
    local native_dimension = dimension(dimension_id)
    local object = handles.WaypointFactory:createClientWaypoint(
        JM_MOD_ID, position, name, native_dimension, false)
    if object == nil then return end
    object:setPersistent(false); object:setEnabled(true); object:setColor(color)
    client:addWaypoint(JM_MOD_ID, object)
    state = {object = object, name = name}; managed[id] = state
  end
  if state.group ~= group_key and assign_group(state.object, group_key) then state.group = group_key end
  local fingerprint = tostring(x) .. "|" .. tostring(y) .. "|" .. tostring(z)
      .. "|" .. tostring(color)
  state.pending_delete = false
  if state.fingerprint == fingerprint then return end
  state.object:setPos(x, y, z); state.object:setColor(color); state.object:setEnabled(true)
  state.fingerprint = fingerprint
end

local function sync_players(players, enabled, relations)
  local managed = managed_players
  local prefix = "player:"
  local setting_enabled = settings.show_remote_players
  if not enabled or not setting_enabled or probe().status ~= "AVAILABLE" then clear(managed); return end
  local world, active = snapshots.world(), {}
  for _, player in pairs(players or {}) do
    if player.position ~= nil and player.uuid ~= world.localPlayerId
        and (player.dimension == nil or player.dimension == "" or player.dimension == world.dimension) then
      local id = prefix .. player.uuid; active[id] = true
      local relation = relations ~= nil and relations[player.uuid] or nil
      local color = relation ~= nil and relation.resolved and relation.color or 0xFF5555
      upsert(managed, id, "[TV] " .. (player.name or "Player"),
          math.floor(player.position.x), math.floor(player.position.y), math.floor(player.position.z),
          world.dimension, color, "online")
    end
  end
  local stale = {}; for id, _ in pairs(managed) do if not active[id] then table.insert(stale, id) end end
  for _, id in ipairs(stale) do remove(managed, id) end
end

local function sync_last_seen(players, enabled, relations)
  if not enabled or not settings.show_last_seen_players or probe().status ~= "AVAILABLE" then clear(managed_last_seen); return end
  local world, active = snapshots.world(), {}
  for _, player in pairs(players or {}) do
    if player.position ~= nil and (player.dimension == nil or player.dimension == ""
        or player.dimension == world.dimension) then
      local id = "last-seen:" .. player.uuid; active[id] = true
      local local_time = handles.LastSeenTimeFormatter:format(player.lastSeenAtUtcMs)
      local relation = relations ~= nil and relations[player.uuid] or nil
      local color = relation ~= nil and relation.resolved and relation.color or 0xFF9A26
      upsert(managed_last_seen, id, "[TV Last] " .. (player.name or "Player") .. " @ " .. local_time,
          math.floor(player.position.x), math.floor(player.position.y), math.floor(player.position.z),
          world.dimension, color, "offline")
    end
  end
  local stale = {}; for id, _ in pairs(managed_last_seen) do if not active[id] then table.insert(stale, id) end end
  for _, id in ipairs(stale) do remove(managed_last_seen, id) end
end

local function list_local()
  local client = api(); if client == nil then return {} end
  local result = {}
  local iterator = handles.iterableIterator:invoke(client:getAllWaypoints(), nil)
  while handles.iteratorHasNext:invoke(iterator, nil) == true do
    local value = handles.iteratorNext:invoke(iterator, nil)
    if value ~= nil and tostring(value:getModId()) ~= JM_MOD_ID then
      local pos, name = value:getBlockPos(), tostring(value:getName() or "Waypoint")
      table.insert(result, {nativeId = tostring(value:getGuid()), name = name,
        symbol = string.sub(name, 1, 1), x = pos:getX(), y = pos:getY(), z = pos:getZ(),
        dimension = tostring(value:getPrimaryDimension() or ""), color = value:getColor()})
    end
  end
  return result
end

-- 3. Capability registration / 能力注册
tv.register_remote_player_projection({id = "journeymap-players",
  probe = probe, sync = function(players, enabled, relations)
    sync_players(players, enabled, relations)
  end, sync_last_seen = sync_last_seen, clear = function() clear(managed_players) end,
  needs_reconcile = function() return has_pending(managed_players) or has_pending(managed_last_seen) end})
tv.register_remote_player_projection({id = "journeymap-player-beacons",
  probe = probe, sync = function(players, enabled)
    -- The API family has no per-waypoint presentation controls. The players capability owns
    -- the single native waypoint; this compatibility capability intentionally performs no work.
  end, clear = function() end})
tv.register_shared_waypoint_adapter({id = "journeymap-shared-waypoints", probe = probe,
  list_local = list_local,
  upsert_remote = function(command) upsert(managed_waypoints, command.waypointId, command.name,
      command.x, command.y, command.z, command.dimension, command.color, shared_group(command)) end,
  delete_remote = function(id) remove(managed_waypoints, id) end,
  clear_remote = function() clear(managed_waypoints) end,
  needs_reconcile = function() return has_pending(managed_waypoints) end})
-- 4. Lifecycle cleanup / 生命周期清理
tv.on_enable(function() initialize() end)
tv.on_disable(function()
  clear(managed_players); clear(managed_last_seen); clear(managed_waypoints); reset_group_scope()
end)
tv.on_play_session_started(function() reset_group_scope() end)
tv.on_play_session_ended(function()
  clear(managed_players); clear(managed_last_seen); clear(managed_waypoints); reset_group_scope()
end)
tv.on_settings_changed(function(key, value)
  if key == "show_remote_players" and not value then clear(managed_players) end
  if key == "show_last_seen_players" and not value then clear(managed_last_seen) end
end)
