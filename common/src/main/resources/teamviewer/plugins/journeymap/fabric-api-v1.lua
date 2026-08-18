-- JourneyMap API 1.x displayable waypoint adapter.
-- JourneyMap API 1.x Displayable 路标适配器。

local MOD_ID, JM_MOD_ID = "journeymap", "teamviewer"
local handles, handle_error = nil, nil
local managed_players, managed_waypoints = {}, {}
local client_objects = services.get("minecraft.client_objects")

local function configure_settings(available)
  tv.configure_setting({key = "show_remote_players", visible = available, enabled = available,
    detail = "Map and world visibility follow JourneyMap's global waypoint settings"})
  tv.configure_setting({key = "show_map_markers", visible = false, enabled = false})
  tv.configure_setting({key = "show_beacons", visible = false, enabled = false})
end

configure_settings(mods.is_loaded(MOD_ID))

local function initialize()
  if handles ~= nil or handle_error ~= nil then return handles ~= nil end
  local ok, value = pcall(function()
    return {
      Waypoint = java.type("journeymap.client.api.display.Waypoint"),
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
  if client_objects == nil then
    return {status = "FAILED", detail = "minecraft.client_objects service is unavailable"}
  end
  if not initialize() then
    configure_settings(false)
    return {status = "UNSUPPORTED_VERSION", detail = handle_error}
  end
  if api() == nil then
    return {status = "ENTRYPOINT_NOT_READY", detail = "JourneyMap IClientAPI is not initialized"}
  end
  return {status = "AVAILABLE", detail = ""}
end

local function remove(managed, id)
  local value = managed[id]
  if value ~= nil and api() ~= nil then pcall(function() api():remove(value.object) end) end
  managed[id] = nil
end

local function clear(managed)
  local ids = {}; for id, _ in pairs(managed) do table.insert(ids, id) end
  for _, id in ipairs(ids) do remove(managed, id) end
end

local function upsert(managed, id, name, x, y, z, dimension_id, color)
  local client = api(); if client == nil then return end
  local state = managed[id]
  if state ~= nil and (state.name ~= name or state.dimension ~= dimension_id) then
    remove(managed, id); state = nil
  end
  local position = client_objects:blockPosition(x, y, z)
  if state == nil then
    local object = java["new"]("journeymap.client.api.display.Waypoint",
        JM_MOD_ID, id, name, client_objects:dimensionKey(dimension_id), position)
    if object == nil then return end
    object:setPersistent(false); object:setEnabled(true); object:setColor(color)
    client:show(object)
    state = {object = object, name = name, dimension = dimension_id}; managed[id] = state
  else
    state.object:setPosition(dimension_id, position)
    state.object:setColor(color); state.object:setEnabled(true); state.object:setDirty()
  end
end

local function sync_players(players, enabled, relations)
  if not enabled or not settings.show_remote_players or probe().status ~= "AVAILABLE" then
    clear(managed_players); return
  end
  local world, active = snapshots.world(), {}
  for _, player in pairs(players or {}) do
    if player.position ~= nil and player.uuid ~= world.localPlayerId
        and (player.dimension == nil or player.dimension == "" or player.dimension == world.dimension) then
      local id = "player:" .. player.uuid; active[id] = true
      local relation = relations ~= nil and relations[player.uuid] or nil
      local color = relation ~= nil and relation.resolved and relation.color or 0xFF5555
      upsert(managed_players, id, "[TV] " .. (player.name or "Player"),
          math.floor(player.position.x), math.floor(player.position.y), math.floor(player.position.z),
          world.dimension, color)
    end
  end
  local stale = {}; for id, _ in pairs(managed_players) do
    if not active[id] then table.insert(stale, id) end
  end
  for _, id in ipairs(stale) do remove(managed_players, id) end
end

local function list_local()
  local client = api(); if client == nil then return {} end
  local result = {}
  local iterator = handles.iterableIterator:invoke(client:getAllWaypoints(), nil)
  while handles.iteratorHasNext:invoke(iterator, nil) == true do
    local value = handles.iteratorNext:invoke(iterator, nil)
    if value ~= nil and tostring(value:getModId()) ~= JM_MOD_ID then
      local pos, name = value:getPosition(), tostring(value:getName() or "Waypoint")
      table.insert(result, {nativeId = tostring(value:getGuid()), name = name,
        symbol = string.sub(name, 1, 1), x = pos:getX(), y = pos:getY(), z = pos:getZ(),
        dimension = tostring(value:getDimension() or ""), color = tonumber(value:getColor()) or 0xFFFFFF})
    end
  end
  return result
end

tv.register_remote_player_projection({id = "journeymap-players", probe = probe,
  sync = sync_players, clear = function() clear(managed_players) end})
tv.register_remote_player_projection({id = "journeymap-player-beacons", probe = probe,
  sync = function(players, enabled) end, clear = function() end})
tv.register_shared_waypoint_adapter({id = "journeymap-shared-waypoints", probe = probe,
  list_local = list_local,
  upsert_remote = function(command) upsert(managed_waypoints, command.waypointId, command.name,
      command.x, command.y, command.z, command.dimension, command.color) end,
  delete_remote = function(id) remove(managed_waypoints, id) end,
  clear_remote = function() clear(managed_waypoints) end})

tv.on_enable(function() initialize() end)
tv.on_disable(function() clear(managed_players); clear(managed_waypoints) end)
tv.on_settings_changed(function(key, value)
  if key == "show_remote_players" and not value then clear(managed_players) end
end)
