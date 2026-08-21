-- Xaero legacy waypoint-session adapter (1.19.3 and 1.20.3 fixed artifacts).
-- Xaero 旧版路标会话适配器（1.19.3 与 1.20.3 固定工件）。

local WORLD_ID, MINIMAP_ID = "xaero-worldmap", "xaero-minimap"
local TRACKER_ID, LAST_SEEN_TRACKER_ID, WAYPOINT_PREFIX, LAST_SEEN_WAYPOINT_PREFIX =
    "teamviewer_remote_players", "teamviewer_last_seen_players", "[TV] ", "[TV Last] "
local world_handles, minimap_handles, world_error, minimap_error = nil, nil, nil, nil
local tracked_players, tracked_last_seen, managed_waypoints, managed_last_seen = nil, nil, {}, {}
local tracker_registered, last_seen_minimap_reconcile, last_seen_context_list = false, false, nil
local client_objects = services.get("minecraft.client_objects")

local function configure_settings()
  local world_available = mods.is_loaded("xaeroworldmap")
  local minimap_available = mods.is_loaded("xaerominimap")
  tv.configure_setting({key = "show_online_world_map", visible = world_available, enabled = world_available})
  tv.configure_setting({key = "show_offline_world_map", visible = world_available, enabled = world_available})
  tv.configure_setting({key = "show_offline_minimap", visible = minimap_available, enabled = minimap_available})
end

configure_settings()

local function load_world()
  if world_handles ~= nil or world_error ~= nil then return world_handles ~= nil end
  local ok, value = pcall(function() return {
    WorldMap = java.type("xaero.map.WorldMap"), ArrayList = java.type("java.util.ArrayList"),
    UUID = java.type("java.util.UUID"),
    Position3D = java.type("fun.prof_chen.teamviewer.main_code.model.Position3D"),
    RemotePlayerInfo = java.type("fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo")
  } end)
  if ok then world_handles = value else world_error = tostring(value) end
  return world_handles ~= nil
end

local function load_minimap()
  if minimap_handles ~= nil or minimap_error ~= nil then return minimap_handles ~= nil end
  local ok, value = pcall(function() return {
    Session = java.type("xaero.common.XaeroMinimapSession"),
    Waypoint = java.type("xaero.common.minimap.waypoints.Waypoint"),
    LastSeenTimeFormatter = java.type("fun.prof_chen.teamviewer.main_code.time.LastSeenTimeFormatter"),
    iterableIterator = java.method("java.lang.Iterable", "iterator"),
    iteratorHasNext = java.method("java.util.Iterator", "hasNext"),
    iteratorNext = java.method("java.util.Iterator", "next")
  } end)
  if ok then minimap_handles = value else minimap_error = tostring(value) end
  return minimap_handles ~= nil
end

local function world_probe()
  if not mods.is_loaded("xaeroworldmap") then
    return {status = "MOD_NOT_INSTALLED", detail = "xaeroworldmap is not installed"}
  end
  if not load_world() then return {status = "UNSUPPORTED_VERSION", detail = world_error} end
  if client_objects == nil then return {status = "FAILED", detail = "minecraft.client_objects is unavailable"} end
  if world_handles.WorldMap.playerTrackerSystemManager == nil then
    return {status = "ENTRYPOINT_NOT_READY", detail = "Xaero tracker manager is not initialized"}
  end
  return {status = "AVAILABLE", detail = ""}
end

local function context()
  if not load_minimap() then return nil end
  local outer = minimap_handles.Session:getCurrentSession()
  local manager = outer ~= nil and outer:getWaypointsManager() or nil
  local world = manager ~= nil and manager:getCurrentWorld() or nil
  local set = world ~= nil and world:getCurrentSet() or nil
  local list = set ~= nil and set:getList() or nil
  if list == nil then return nil end
  return {manager = manager, world = world, set = set, list = list}
end

local function minimap_probe()
  if not mods.is_loaded("xaerominimap") then
    return {status = "MOD_NOT_INSTALLED", detail = "xaerominimap is not installed"}
  end
  if not load_minimap() then return {status = "UNSUPPORTED_VERSION", detail = minimap_error} end
  local ok, ready = pcall(function() return context() ~= nil end)
  if not ok then return {status = "FAILED", detail = "Xaero legacy session probe failed: " .. tostring(ready)} end
  if not ready then return {status = "ENTRYPOINT_NOT_READY", detail = "Xaero waypoint session is not initialized"} end
  return {status = "AVAILABLE", detail = ""}
end

local function install_tracker()
  if tracker_registered or world_probe().status ~= "AVAILABLE" then return end
  tracked_players = java["new"](world_handles.ArrayList)
  tracked_last_seen = java["new"](world_handles.ArrayList)
  local reader = java.proxy("xaero.map.radar.tracker.system.ITrackedPlayerReader", {
    getId = function(player) return player:uuid() end,
    getX = function(player) return player:position():x() end,
    getY = function(player) return player:position():y() end,
    getZ = function(player) return player:position():z() end,
    getDimension = function(player) return client_objects:dimensionKey(player:dimension()) end
  })
  local function register(id, values)
    local system = java.proxy("xaero.map.radar.tracker.system.IPlayerTrackerSystem", {
      getReader = function() return reader end,
      getTrackedPlayerIterator = function() return values:iterator() end
    })
    world_handles.WorldMap.playerTrackerSystemManager:register(id, system)
  end
  register(LAST_SEEN_TRACKER_ID, tracked_last_seen)
  register(TRACKER_ID, tracked_players)
  tracker_registered = true
end

local function sync_last_seen_world(players, enabled)
  install_tracker(); if tracked_last_seen == nil then return end
  tracked_last_seen:clear(); if not enabled then return end
  for _, player in pairs(players or {}) do
    if player.position ~= nil then
      tracked_last_seen:add(java["new"](world_handles.RemotePlayerInfo,
          world_handles.UUID:fromString(player.uuid),
          java["new"](world_handles.Position3D, player.position.x, player.position.y, player.position.z),
          player.dimension, "[Last] " .. player.name))
    end
  end
end

local function sync_players(players, enabled)
  install_tracker(); if tracked_players == nil then return end
  tracked_players:clear(); if not enabled then return end
  for _, player in pairs(players or {}) do
    if player.position ~= nil then
      tracked_players:add(java["new"](world_handles.RemotePlayerInfo,
          world_handles.UUID:fromString(player.uuid),
          java["new"](world_handles.Position3D, player.position.x, player.position.y, player.position.z),
          player.dimension, player.name))
    end
  end
end

local function changed(value)
  pcall(function() value.manager.setChanged = os.time() * 1000 end)
  pcall(function() value.manager:updateWaypoints() end)
end

local function remove_stored_last_seen(value)
  local stale, iterator = {}, minimap_handles.iterableIterator:invoke(value.list, nil)
  while minimap_handles.iteratorHasNext:invoke(iterator, nil) == true do
    local waypoint = minimap_handles.iteratorNext:invoke(iterator, nil)
    local name = tostring(waypoint:getName() or "")
    if string.sub(name, 1, #LAST_SEEN_WAYPOINT_PREFIX) == LAST_SEEN_WAYPOINT_PREFIX then
      table.insert(stale, waypoint)
    end
  end
  for _, waypoint in ipairs(stale) do value.list:remove(waypoint) end
  return #stale > 0
end

local function reconcile_last_seen_context(value)
  if last_seen_context_list == value.list then return false end
  managed_last_seen = {}
  last_seen_context_list = value.list
  return remove_stored_last_seen(value)
end

local function is_managed(value)
  for _, managed in pairs(managed_waypoints) do if managed == value then return true end end
  for _, managed in pairs(managed_last_seen) do if managed.object == value then return true end end
  return false
end

local function clear_last_seen_minimap()
  local value = context()
  if value == nil then last_seen_minimap_reconcile = true; return end
  local dirty = reconcile_last_seen_context(value)
  for _, state in pairs(managed_last_seen) do value.list:remove(state.object) end
  if remove_stored_last_seen(value) then dirty = true end
  if next(managed_last_seen) ~= nil then dirty = true end
  managed_last_seen = {}; last_seen_minimap_reconcile = false
  if dirty then changed(value) end
end

local function sync_last_seen_minimap(players, enabled)
  if not enabled then clear_last_seen_minimap(); return end
  local value = context()
  if value == nil then last_seen_minimap_reconcile = true; return end
  local active, dirty = {}, reconcile_last_seen_context(value)
  if next(managed_last_seen) == nil and remove_stored_last_seen(value) then dirty = true end
  for _, player in pairs(players or {}) do
    if player.position ~= nil then
      local id = "last-seen:" .. player.uuid
      local local_time = minimap_handles.LastSeenTimeFormatter:format(player.lastSeenAtUtcMs)
      local name = LAST_SEEN_WAYPOINT_PREFIX .. (player.name or "Player") .. " @ " .. local_time
      local signature = name .. ":" .. player.position.x .. ":" .. player.position.y .. ":" .. player.position.z
      active[id] = true
      local state = managed_last_seen[id]
      if state == nil or state.signature ~= signature then
        if state ~= nil then value.list:remove(state.object) end
        local object = java["new"](minimap_handles.Waypoint,
            math.floor(player.position.x), math.floor(player.position.y), math.floor(player.position.z),
            name, "L", 0xFF9A26)
        pcall(function() object:setYIncluded(true) end)
        value.list:add(object); managed_last_seen[id] = {object = object, signature = signature}; dirty = true
      end
    end
  end
  local stale = {}; for id, _ in pairs(managed_last_seen) do if not active[id] then table.insert(stale, id) end end
  for _, id in ipairs(stale) do value.list:remove(managed_last_seen[id].object); managed_last_seen[id] = nil; dirty = true end
  last_seen_minimap_reconcile = false; if dirty then changed(value) end
end

local function list_local()
  local value = context(); if value == nil then return {} end
  local result, iterator = {}, minimap_handles.iterableIterator:invoke(value.list, nil)
  while minimap_handles.iteratorHasNext:invoke(iterator, nil) == true do
    local waypoint = minimap_handles.iteratorNext:invoke(iterator, nil)
    local name = tostring(waypoint:getName() or "")
    if not is_managed(waypoint) and string.sub(name, 1, #WAYPOINT_PREFIX) ~= WAYPOINT_PREFIX
        and string.sub(name, 1, #LAST_SEEN_WAYPOINT_PREFIX) ~= LAST_SEEN_WAYPOINT_PREFIX then
      table.insert(result, {nativeId = name, name = name, symbol = tostring(waypoint:getSymbol() or "W"),
        x = waypoint:getX(), y = waypoint:getY(), z = waypoint:getZ(), dimension = "",
        color = waypoint:getColor()})
    end
  end
  return result
end

local function delete_remote(id)
  local value, waypoint = context(), managed_waypoints[id]
  if value == nil or waypoint == nil then return end
  value.list:remove(waypoint); managed_waypoints[id] = nil; changed(value)
end

local function upsert_remote(command)
  local value = context(); if value == nil then return end
  if managed_waypoints[command.waypointId] ~= nil then
    value.list:remove(managed_waypoints[command.waypointId])
  end
  local waypoint = java["new"](minimap_handles.Waypoint, command.x, command.y, command.z,
      WAYPOINT_PREFIX .. command.name, command.symbol or "W", command.color)
  pcall(function() waypoint:setYIncluded(true) end)
  value.list:add(waypoint); managed_waypoints[command.waypointId] = waypoint; changed(value)
end

local function clear_remote()
  local value = context()
  if value == nil then managed_waypoints = {}; return end
  for _, waypoint in pairs(managed_waypoints) do value.list:remove(waypoint) end
  managed_waypoints = {}; changed(value)
end

tv.register_remote_player_projection({id = WORLD_ID, probe = world_probe,
  sync = function(players, enabled) sync_players(players, enabled and settings.show_online_world_map) end,
  sync_last_seen = function(players, enabled)
    sync_last_seen_world(players, enabled and settings.show_offline_world_map)
  end,
  clear = function() sync_players({}, false) end})
tv.register_remote_player_projection({id = "xaero-last-seen-minimap", probe = minimap_probe,
  sync = function(players, enabled) end,
  sync_last_seen = function(players, enabled)
    sync_last_seen_minimap(players, enabled and settings.show_offline_minimap)
  end,
  clear = clear_last_seen_minimap,
  needs_reconcile = function() return last_seen_minimap_reconcile end})
tv.register_shared_waypoint_adapter({id = MINIMAP_ID, probe = minimap_probe,
  list_local = list_local, upsert_remote = upsert_remote,
  delete_remote = delete_remote, clear_remote = clear_remote})
tv.on_enable(function() install_tracker() end)
tv.on_disable(function() sync_players({}, false); sync_last_seen_world({}, false);
  clear_last_seen_minimap(); clear_remote() end)
tv.on_settings_changed(function(key, value)
  if key == "show_online_world_map" and not value then sync_players({}, false) end
  if key == "show_offline_world_map" and not value then sync_last_seen_world({}, false) end
  if key == "show_offline_minimap" and not value then clear_last_seen_minimap() end
end)
