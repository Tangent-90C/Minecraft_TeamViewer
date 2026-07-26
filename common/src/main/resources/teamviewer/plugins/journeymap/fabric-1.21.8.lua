-- JourneyMap 1.21.8 service → Lua Adapter → common capability contracts.
-- JourneyMap 1.21.8 入口服务 → Lua Adapter → common 能力契约。

local MOD_ID, JM_MOD_ID = "journeymap", "teamviewer"
local handles, handle_error = nil, nil
local managed_markers, managed_beacons, managed_waypoints = {}, {}, {}
local client_objects = services.get("minecraft.client_objects")

-- 1. Version-specific handles and dynamic service / 版本句柄与动态服务
local function initialize()
  if handles ~= nil or handle_error ~= nil then return handles ~= nil end
  local ok, value = pcall(function()
    return {
      WaypointFactory = java.type("journeymap.api.v2.common.waypoint.WaypointFactory"),
      iterableIterator = java.method("java.lang.Iterable", "iterator"),
      iteratorHasNext = java.method("java.util.Iterator", "hasNext"),
      iteratorNext = java.method("java.util.Iterator", "next")
    }
  end)
  if ok then handles = value else handle_error = tostring(value) end
  return handles ~= nil
end

local function api() return services.get("journeymap.client_api") end
local function probe()
  if not mods.is_loaded(MOD_ID) then
    return {status = "MOD_NOT_INSTALLED", detail = "journeymap is not installed"}
  end
  if client_objects == nil then return {status = "FAILED", detail = "minecraft.client_objects service is unavailable"} end
  if not initialize() then return {status = "UNSUPPORTED_VERSION", detail = handle_error} end
  if api() == nil then return {status = "ENTRYPOINT_NOT_READY", detail = "JourneyMap IClientAPI is not initialized"} end
  return {status = "AVAILABLE", detail = ""}
end

local function dimension(id)
  return client_objects:dimensionKey(id)
end

local function remove(managed, id)
  local value = managed[id]
  if value ~= nil and api() ~= nil then pcall(function() api():removeWaypoint(JM_MOD_ID, value.object) end) end
  managed[id] = nil
end

local function clear(managed)
  local ids = {}; for id, _ in pairs(managed) do table.insert(ids, id) end
  for _, id in ipairs(ids) do remove(managed, id) end
end

-- 2. Common object -> JourneyMap object / common 对象 -> JourneyMap 对象
local function upsert(managed, id, name, x, y, z, dimension_id, color, presentation)
  local client = api(); if client == nil then return end
  local state = managed[id]
  if state ~= nil and state.name ~= name then remove(managed, id); state = nil end
  if state == nil then
    local position = client_objects:blockPosition(x, y, z)
    local native_dimension = dimension(dimension_id)
    local object = handles.WaypointFactory:createClientWaypoint(
        JM_MOD_ID, position, name, native_dimension, false)
    if object == nil then return end
    object:setPersistent(false); object:setEnabled(true); object:setColor(color)
    if presentation == "marker" then
      object:setShowBeacon(false); object:setShowOnMap(true); object:setShowInWorld(false)
    elseif presentation == "beacon" then
      object:setShowBeacon(true); object:setShowOnMap(false); object:setShowInWorld(true)
    end
    client:addWaypoint(JM_MOD_ID, object)
    state = {object = object, name = name}; managed[id] = state
  end
  state.object:setPos(x, y, z); state.object:setColor(color); state.object:setEnabled(true)
end

local function sync_players(managed, prefix, presentation, players, enabled, setting_enabled)
  if not enabled or not setting_enabled or probe().status ~= "AVAILABLE" then clear(managed); return end
  local world, active = snapshots.world(), {}
  for _, player in pairs(players or {}) do
    if player.position ~= nil and player.uuid ~= world.localPlayerId
        and (player.dimension == nil or player.dimension == "" or player.dimension == world.dimension) then
      local id = prefix .. player.uuid; active[id] = true
      upsert(managed, id, "[TV] " .. (player.name or "Player"),
          math.floor(player.position.x), math.floor(player.position.y), math.floor(player.position.z),
          world.dimension, 0xFF5555, presentation)
    end
  end
  local stale = {}; for id, _ in pairs(managed) do if not active[id] then table.insert(stale, id) end end
  for _, id in ipairs(stale) do remove(managed, id) end
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
        dimension = "", color = value:getColor()})
    end
  end
  return result
end

-- 3. Capability registration / 能力注册
tv.register_remote_player_projection({id = "journeymap-players", kind = "JOURNEYMAP_MAP_MARKER",
  probe = probe, sync = function(players, enabled)
    sync_players(managed_markers, "player-marker:", "marker", players, enabled, settings.show_map_markers)
  end, clear = function() clear(managed_markers) end})
tv.register_remote_player_projection({id = "journeymap-player-beacons", kind = "JOURNEYMAP_BEACON",
  probe = probe, sync = function(players, enabled)
    sync_players(managed_beacons, "player-beacon:", "beacon", players, enabled, settings.show_beacons)
  end, clear = function() clear(managed_beacons) end})
tv.register_shared_waypoint_adapter({id = "journeymap-shared-waypoints", probe = probe,
  list_local = list_local,
  upsert_remote = function(command) upsert(managed_waypoints, command.waypointId, command.name,
      command.x, command.y, command.z, command.dimension, command.color, "waypoint") end,
  delete_remote = function(id) remove(managed_waypoints, id) end,
  clear_remote = function() clear(managed_waypoints) end})
-- 4. Lifecycle cleanup / 生命周期清理
tv.on_enable(function() initialize() end)
tv.on_disable(function() clear(managed_markers); clear(managed_beacons); clear(managed_waypoints) end)
tv.on_settings_changed(function(key, value)
  if key == "show_beacons" and not value then clear(managed_beacons) end
  if key == "show_map_markers" and not value then clear(managed_markers) end
end)
