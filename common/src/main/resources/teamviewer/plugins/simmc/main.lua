-- SimMC RegionManager → TeamViewRelay absolute battle-map cells.
-- SimMC RegionManager → TeamViewRelay 绝对区块战局地图单元格。

local CAPABILITY_ID = "simmc-native-battle-map"
local MOD_ID = "smcmod"
local initialized = false
local initialization_error = nil
local capture_error = nil
local manager_field, chunk_to_region_field, color_method, core_method
local entry_set_method, iterator_method, has_next_method, next_method, entry_key_method, entry_value_method

-- 1. External API handle initialization / 外部 API 句柄初始化
local function initialize_handles()
  if initialized then return initialization_error == nil end
  initialized = true
  local ok, error_value = pcall(function()
    -- Cache reflective handles once. Unknown SimMC versions fail here and remain diagnosable.
    -- 反射句柄只解析一次；未知 SimMC 版本会在这里留下可诊断错误。
    manager_field = java.field("com.simmc.mod.region.RegionManager", "regionManager")
    chunk_to_region_field = java.field("com.simmc.mod.region.RegionManagerImpl", "chunkToRegion")
    color_method = java.method("com.simmc.mod.region.Region", "color")
    core_method = java.method("com.simmc.mod.region.Region", "isCore")
    -- Resolve collection methods against public interfaces. Calling methods discovered from
    -- JDK-private LinkedHashMap implementation classes is blocked by Java's module system.
    entry_set_method = java.method("java.util.Map", "entrySet")
    iterator_method = java.method("java.util.Set", "iterator")
    has_next_method = java.method("java.util.Iterator", "hasNext")
    next_method = java.method("java.util.Iterator", "next")
    entry_key_method = java.method("java.util.Map$Entry", "getKey")
    entry_value_method = java.method("java.util.Map$Entry", "getValue")
  end)
  if not ok then initialization_error = tostring(error_value) end
  return initialization_error == nil
end

-- 2. Version and failure probe / 版本与故障探测
local function probe()
  if not mods.is_loaded(MOD_ID) then
    return {status = "MOD_NOT_INSTALLED", detail = "smcmod is not installed"}
  end
  if not initialize_handles() then
    return {status = "UNSUPPORTED_VERSION", detail = initialization_error}
  end
  if capture_error ~= nil then
    return {status = "FAILED", detail = capture_error}
  end
  return {status = "AVAILABLE", detail = ""}
end

local function chunk_coordinate(pos, name)
  -- Yarn 1.21 exposes ChunkPos.x/z fields, while newer mappings expose x()/z(). Accept both
  -- shapes so only the Lua adapter owns this version boundary.
  -- Yarn 1.21 使用 x/z 字段，新映射使用 x()/z()；两种签名都在 Lua 适配层处理。
  local ok, value = pcall(function() return pos[name] end)
  if ok and type(value) == "number" then return value end
  if ok and type(value) == "function" then
    local called, result = pcall(value, pos)
    if called and type(result) == "number" then return result end
  end
  local getter = "get" .. string.upper(string.sub(name, 1, 1)) .. string.sub(name, 2)
  local called, result = pcall(function() return pos[getter](pos) end)
  if called and type(result) == "number" then return result end
  error("Unsupported ChunkPos " .. name .. " accessor")
end

-- 3. SimMC map -> common absolute cells / SimMC 地图 -> common 绝对单元格
local function capture()
  if probe().status ~= "AVAILABLE" then return nil end
  local world = snapshots.world()
  if world == nil or world.dimension == nil then return nil end

  local ok, result = pcall(function()
    local manager = manager_field:get(nil)
    if manager == nil then return nil end
    local regions = chunk_to_region_field:get(manager)
    if regions == nil then return nil end
    local cells = {}
    local entries = entry_set_method:invoke(regions, nil)
    local iterator = iterator_method:invoke(entries, nil)
    while has_next_method:invoke(iterator, nil) == true do
      local entry = next_method:invoke(iterator, nil)
      local pos = entry_key_method:invoke(entry, nil)
      local region = entry_value_method:invoke(entry, nil)
      if pos ~= nil and region ~= nil then
        local color = tonumber(color_method:invoke(region, nil)) or 0xFFFFFF
        local is_core = core_method:invoke(region, nil) == true
        table.insert(cells, {
          x = chunk_coordinate(pos, "x"),
          z = chunk_coordinate(pos, "z"),
          symbol = is_core and "╫" or "",
          color = string.format("#%06X", color % 0x1000000)
        })
      end
    end
    if #cells == 0 then return nil end
    return {
      dimension = world.dimension,
      observedAt = os.time() * 1000,
      coordinateSpace = "absolute_chunk",
      mapSize = 0, anchorRow = 0, anchorColumn = 0,
      cells = cells
    }
  end)
  if not ok then
    capture_error = "SimMC capture failed: " .. tostring(result)
    tv.log.error(capture_error)
    return nil
  end
  return result
end

-- 4. Capability and lifecycle / 能力注册与生命周期
tv.register_battle_map_source({id = CAPABILITY_ID, probe = probe, capture = capture})
tv.on_enable(function() initialize_handles() end)
tv.on_disable(function() end)
tv.on_settings_changed(function(key, value) end)
