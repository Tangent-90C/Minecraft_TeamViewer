-- JourneyMap API v2 without per-waypoint presentation controls.
-- 不支持逐路标显示控制的 JourneyMap API v2。

local MOD_ID, JM_MOD_ID = "journeymap", "teamviewer"
local handles, handle_error = nil, nil
local managed_players, managed_last_seen, managed_waypoints = {}, {}, {}
local client_objects = services.get("minecraft.client_objects")

local function configure_settings(available)
  tv.configure_setting({key = "show_remote_players", visible = available, enabled = available,
    detail = "Map and world visibility follow JourneyMap's global waypoint settings"})
  tv.configure_setting({key = "show_map_markers", visible = false, enabled = false})
  tv.configure_setting({key = "show_beacons", visible = false, enabled = false})
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
  return {status = "AVAILABLE", detail = ""}
end

local function dimension(id)
  return client_objects:dimensionKey(id)
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
local function upsert(managed, id, name, x, y, z, dimension_id, color)
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
  state.pending_delete = false
  state.object:setPos(x, y, z); state.object:setColor(color); state.object:setEnabled(true)
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
          world.dimension, color)
    end
  end
  local stale = {}; for id, _ in pairs(managed) do if not active[id] then table.insert(stale, id) end end
  for _, id in ipairs(stale) do remove(managed, id) end
end

local function sync_last_seen(players, enabled)
  if not enabled or probe().status ~= "AVAILABLE" then clear(managed_last_seen); return end
  local world, active = snapshots.world(), {}
  for _, player in pairs(players or {}) do
    if player.position ~= nil and (player.dimension == nil or player.dimension == ""
        or player.dimension == world.dimension) then
      local id = "last-seen:" .. player.uuid; active[id] = true
      local local_time = handles.LastSeenTimeFormatter:format(player.lastSeenAtUtcMs)
      upsert(managed_last_seen, id, "[TV Last] " .. (player.name or "Player") .. " @ " .. local_time,
          math.floor(player.position.x), math.floor(player.position.y), math.floor(player.position.z),
          world.dimension, 0xFF9A26)
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
      command.x, command.y, command.z, command.dimension, command.color) end,
  delete_remote = function(id) remove(managed_waypoints, id) end,
  clear_remote = function() clear(managed_waypoints) end,
  needs_reconcile = function() return has_pending(managed_waypoints) end})
-- 4. Lifecycle cleanup / 生命周期清理
tv.on_enable(function() initialize() end)
tv.on_disable(function() clear(managed_players); clear(managed_last_seen); clear(managed_waypoints) end)
tv.on_settings_changed(function(key, value)
  if key == "show_remote_players" and not value then clear(managed_players) end
end)
