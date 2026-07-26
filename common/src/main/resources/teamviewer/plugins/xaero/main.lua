-- Xaero World Map/Minimap Lua adapter. Each capability probes its own optional mod.
-- Xaero 世界地图/小地图 Lua Adapter；两个能力分别探测各自的可选 Mod。

local WORLD_ID, MINIMAP_ID = "xaero-worldmap", "xaero-minimap"
local TRACKER_ID, WAYPOINT_PREFIX = "teamviewer_remote_players", "[TV] "
local world_handles, minimap_handles = nil, nil
local world_handle_error, minimap_handle_error = nil, nil
local world_runtime_error, minimap_runtime_error = nil, nil
local tracked_players, managed_waypoints = nil, {}
local tracker_registered = false
local client_objects = services.get("minecraft.client_objects")

-- 1. Cache external API handles / 缓存外部 API 句柄
local function load_world_handles()
  if world_handles ~= nil or world_handle_error ~= nil then return world_handles ~= nil end
  local ok, value = pcall(function()
    return {
      WorldMap = java.type("xaero.map.WorldMap"),
      ArrayList = java.type("java.util.ArrayList"),
      UUID = java.type("java.util.UUID"),
      Position3D = java.type("fun.prof_chen.teamviewer.main_code.model.Position3D"),
      RemotePlayerInfo = java.type("fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo")
    }
  end)
  if ok then world_handles = value else world_handle_error = tostring(value) end
  return world_handles ~= nil
end

local function load_minimap_handles()
  if minimap_handles ~= nil or minimap_handle_error ~= nil then return minimap_handles ~= nil end
  local ok, value = pcall(function()
    return {
      Session = java.type("xaero.common.XaeroMinimapSession"),
      Waypoint = java.type("xaero.common.minimap.waypoints.Waypoint"),
      iterableIterator = java.method("java.lang.Iterable", "iterator"),
      iteratorHasNext = java.method("java.util.Iterator", "hasNext"),
      iteratorNext = java.method("java.util.Iterator", "next")
    }
  end)
  if ok then minimap_handles = value else minimap_handle_error = tostring(value) end
  return minimap_handles ~= nil
end

-- 2. Independent capability probes / 独立能力探测
local function world_probe()
  if not mods.is_loaded("xaeroworldmap") then
    return {status = "MOD_NOT_INSTALLED", detail = "xaeroworldmap is not installed"}
  end
  if not load_world_handles() then
    return {status = "UNSUPPORTED_VERSION", detail = "xaeroworldmap " .. environment.mod_version("xaeroworldmap")
        .. " API mismatch: " .. world_handle_error}
  end
  if client_objects == nil then return {status = "FAILED", detail = "minecraft.client_objects service is unavailable"} end
  if world_runtime_error ~= nil then return {status = "FAILED", detail = world_runtime_error} end
  if world_handles.WorldMap.playerTrackerSystemManager == nil then
    return {status = "ENTRYPOINT_NOT_READY", detail = "Xaero World Map tracker manager is not initialized"}
  end
  return {status = "AVAILABLE", detail = ""}
end

local function minimap_probe()
  if not mods.is_loaded("xaerominimap") then
    return {status = "MOD_NOT_INSTALLED", detail = "xaerominimap is not installed"}
  end
  if not load_minimap_handles() then
    return {status = "UNSUPPORTED_VERSION", detail = "xaerominimap " .. environment.mod_version("xaerominimap")
        .. " API mismatch: " .. minimap_handle_error}
  end
  if minimap_runtime_error ~= nil then return {status = "FAILED", detail = minimap_runtime_error} end
  local ok, ready = pcall(function()
    local outer = minimap_handles.Session:getCurrentSession()
    local processor = outer ~= nil and outer:getMinimapProcessor() or nil
    local session = processor ~= nil and processor:getSession() or nil
    local manager = session ~= nil and session:getWorldManager() or nil
    local world = manager ~= nil and manager:getCurrentWorld() or nil
    return world ~= nil and world:getCurrentWaypointSet() ~= nil
  end)
  if not ok then return {status = "FAILED", detail = "Xaero Minimap session probe failed: " .. tostring(ready)} end
  if not ready then return {status = "ENTRYPOINT_NOT_READY", detail = "Xaero Minimap waypoint session is not initialized"} end
  return {status = "AVAILABLE", detail = ""}
end

local function dimension_key(id)
  return client_objects:dimensionKey(id)
end

local function install_tracker()
  if tracker_registered or world_probe().status ~= "AVAILABLE" then return end
  local ok, error_value = pcall(function()
    local manager = world_handles.WorldMap.playerTrackerSystemManager
    if manager == nil then return end
    tracked_players = java["new"](world_handles.ArrayList)
    local reader = java.proxy("xaero.map.radar.tracker.system.ITrackedPlayerReader", {
      getId = function(player) return player:uuid() end,
      getX = function(player) return player:position():x() end,
      getY = function(player) return player:position():y() end,
      getZ = function(player) return player:position():z() end,
      getDimension = function(player) return dimension_key(player:dimension()) end
    })
    local system = java.proxy("xaero.map.radar.tracker.system.IPlayerTrackerSystem", {
      getReader = function() return reader end,
      getTrackedPlayerIterator = function() return tracked_players:iterator() end
    })
    manager:register(TRACKER_ID, system)
    tracker_registered = true
  end)
  if not ok then world_runtime_error = "Xaero tracker registration failed: " .. tostring(error_value) end
end

-- 3. TeamViewRelay players -> Xaero tracker records / 玩家数据 -> Xaero tracker 记录
local function sync_world_map(players, enabled)
  install_tracker()
  if tracked_players == nil then return end
  tracked_players:clear()
  if not enabled then return end
  for _, player in pairs(players or {}) do
    if player.position ~= nil then
      local uuid = world_handles.UUID:fromString(player.uuid)
      local position = java["new"](world_handles.Position3D,
          player.position.x, player.position.y, player.position.z)
      tracked_players:add(java["new"](world_handles.RemotePlayerInfo,
          uuid, position, player.dimension, player.name))
    end
  end
end

-- 4. Xaero local/remote waypoint conversion / Xaero 本地与远程路标转换
local function current_waypoint_context()
  if minimap_probe().status ~= "AVAILABLE" then return nil end
  local outer = minimap_handles.Session:getCurrentSession()
  if outer == nil then return nil end
  local processor = outer:getMinimapProcessor()
  local session = processor ~= nil and processor:getSession() or nil
  if session == nil then return nil end
  local manager = session:getWorldManager()
  local world = manager ~= nil and manager:getCurrentWorld() or nil
  local set = world ~= nil and world:getCurrentWaypointSet() or nil
  if set == nil then return nil end
  return {session = session, world = world, set = set}
end

local function save_context(context)
  local io = context.session:getWorldManagerIO()
  if io ~= nil then io:saveWorld(context.world) end
  pcall(function() context.session:setSetChangedTime(os.time() * 1000) end)
end

local function is_managed(value)
  for _, managed in pairs(managed_waypoints) do if managed == value then return true end end
  return false
end

local function list_local()
  local context = current_waypoint_context()
  if context == nil then return {} end
  local result = {}
  local iterator = minimap_handles.iterableIterator:invoke(context.set:getWaypoints(), nil)
  while minimap_handles.iteratorHasNext:invoke(iterator, nil) == true do
    local value = minimap_handles.iteratorNext:invoke(iterator, nil)
    local name = tostring(value:getName() or "")
    if not is_managed(value) and string.sub(name, 1, #WAYPOINT_PREFIX) ~= WAYPOINT_PREFIX then
      local id_ok, native_id = pcall(function() return value:getId() end)
      table.insert(result, {
        nativeId = tostring(id_ok and native_id or name),
        name = name, symbol = tostring(value:getSymbol() or "W"),
        x = value:getX(), y = value:getY(), z = value:getZ(),
        dimension = "", color = value:getColor()
      })
    end
  end
  return result
end

local function delete_remote(id)
  local context, value = current_waypoint_context(), managed_waypoints[id]
  if context == nil or value == nil then return end
  context.set:remove(value)
  managed_waypoints[id] = nil
  save_context(context)
end

local function upsert_remote(command)
  local context = current_waypoint_context()
  if context == nil then return end
  if managed_waypoints[command.waypointId] ~= nil then
    context.set:remove(managed_waypoints[command.waypointId])
  end
  local symbol = command.symbol or "W"
  local created = java["new"](minimap_handles.Waypoint,
      command.x, command.y, command.z, WAYPOINT_PREFIX .. command.name, symbol, command.color)
  pcall(function() created:setYIncluded(true) end)
  context.set:add(created)
  managed_waypoints[command.waypointId] = created
  save_context(context)
end

local function clear_remote()
  local context = current_waypoint_context()
  if context == nil then managed_waypoints = {}; return end
  for id, value in pairs(managed_waypoints) do context.set:remove(value) end
  managed_waypoints = {}
  save_context(context)
end

-- 5. Capability registration and cleanup / 能力注册与清理
tv.register_remote_player_projection({
  id = WORLD_ID, probe = world_probe,
  sync = sync_world_map, clear = function() sync_world_map({}, false) end
})
tv.register_shared_waypoint_adapter({
  id = MINIMAP_ID, probe = minimap_probe, list_local = list_local,
  upsert_remote = upsert_remote, delete_remote = delete_remote, clear_remote = clear_remote
})
tv.on_enable(function() install_tracker() end)
tv.on_disable(function() sync_world_map({}, false); clear_remote() end)
tv.on_settings_changed(function(key, value) end)
