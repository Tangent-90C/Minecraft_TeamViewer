-- SimMC RegionManager → TeamViewRelay absolute battle-map cells.
-- SimMC RegionManager → TeamViewRelay 绝对区块战局地图单元格。

local CAPABILITY_ID = "simmc-native-battle-map"
local MOD_ID = "smcmod"
local initialized = false
local initialization_error = nil
local capture_error = nil
local manager_field, chunk_to_region_field, color_method, core_method
local entry_set_method, iterator_method, has_next_method, next_method, entry_key_method, entry_value_method
local intermediary_chunk_x_field, intermediary_chunk_z_field

local function optional_field(owner, name)
  local ok, handle = pcall(function() return java.field(owner, name) end)
  if ok then return handle end
  return nil
end

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

    -- SimMC 1.0.0 is distributed in Fabric intermediary mappings. Its map key is therefore
    -- net.minecraft.class_1923 at runtime, whose public x/z fields are field_9181/field_9180.
    -- String class/field names used by Lua are not remapped by Fabric Loader, so the readable
    -- Yarn names cannot be the only access path in a production client.
    --
    -- SimMC 1.0.0 使用 Fabric intermediary 映射发布，因此运行时键类型是
    -- net.minecraft.class_1923，公开的 x/z 字段分别是 field_9181/field_9180。
    -- Lua 字符串中的类名和字段名不会被 Fabric Loader 自动重映射，所以正式客户端
    -- 不能只依赖开发环境里可读的 Yarn 名称。
    intermediary_chunk_x_field =
        optional_field("net.minecraft.class_1923", "field_9181")
    intermediary_chunk_z_field =
        optional_field("net.minecraft.class_1923", "field_9180")
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
  -- Development/test mappings expose x/z directly. Try those shapes first so copied plugins
  -- can also work with readable or JavaBean-style external position objects.
  -- 开发/测试映射会直接暴露 x/z；先兼容这些形式，也方便复制后的插件读取普通外部对象。
  local ok, value = pcall(function() return pos[name] end)
  if ok and type(value) == "number" then return value end
  if ok and type(value) == "function" then
    local called, result = pcall(value, pos)
    if called and type(result) == "number" then return result end
  end
  local getter = "get" .. string.upper(string.sub(name, 1, 1)) .. string.sub(name, 2)
  local called, result = pcall(function() return pos[getter](pos) end)
  if called and type(result) == "number" then return result end

  -- Production Fabric path for the exact SimMC 1.0.0 runtime signature.
  -- SimMC 1.0.0 正式 Fabric 客户端的实际运行时签名。
  local field = name == "x" and intermediary_chunk_x_field or intermediary_chunk_z_field
  if field ~= nil then
    local field_ok, field_value = pcall(function() return field:get(pos) end)
    if field_ok and tonumber(field_value) ~= nil then return tonumber(field_value) end
  end

  error("Unsupported ChunkPos " .. name
      .. " accessor (expected x/z, getX/getZ, or Fabric intermediary field_9181/field_9180)")
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
