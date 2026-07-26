-- NodeMC scoreboard → TeamViewRelay battle-map adapter.
-- NodeMC 计分板 → TeamViewRelay 战局地图 Adapter。
--
-- Data flow / 数据流：Minecraft sidebar snapshot → Lua glyph/color normalization →
-- BattleMapSource table → common history/projection/deduplication/protocol pipeline.

local CAPABILITY_ID = "nodemc-scoreboard-battle-map"
local UTF8_CHARACTER = "[%z\1-\127\194-\244][\128-\191]*"

-- 1. Input normalization / 输入规范化

local function is_map_glyph(symbol)
  -- Match the same Unicode blocks as common's former Java parser: Box Drawing, Block
  -- Elements, Geometric Shapes and Miscellaneous Symbols. Checking UTF-8 bytes keeps the
  -- adapter independent from an optional Lua utf8 library.
  -- 与原 Java 解析器保持一致：仅接受制表线、方块元素、几何形状和杂项符号 Unicode 块。
  local first, second, third = string.byte(symbol, 1, 3)
  if first ~= 0xE2 or second == nil or third == nil then return false end
  return (second == 0x94 or second == 0x95) -- U+2500..U+257F
      or (second == 0x96 and third <= 0x9F) -- U+2580..U+259F
      or (second == 0x96 and third >= 0xA0) or second == 0x97 -- U+25A0..U+25FF
      or second == 0x98 or second == 0x99 -- U+2600..U+26FF
end

local function glyphs_of(line)
  local glyphs = {}
  for _, run in ipairs(line.runs or {}) do
    local color = run.colorRaw
    if color == nil or color == "" then color = "#FFFFFF" end
    for symbol in string.gmatch(run.text or "", UTF8_CHARACTER) do
      if is_map_glyph(symbol) then
        table.insert(glyphs, {symbol = symbol, color = color})
      end
    end
  end
  return glyphs
end

local function range_hint(text)
  local left, center, right = string.match(text or "", "(-?%d+)%s+(-?%d+)%s+(-?%d+)")
  left, center, right = tonumber(left), tonumber(center), tonumber(right)
  if left ~= nil and center == 0 and right ~= nil and left <= right then
    return right - left + 1
  end
  return nil
end

-- 2. Server-format parsing / 服务器格式解析

local function resolve_size(lines, hinted)
  local counts = {}
  for _, line in ipairs(lines) do
    local size = #line.glyphs
    if size > 0 then counts[size] = (counts[size] or 0) + 1 end
  end
  if hinted ~= nil and hinted > 0 and (counts[hinted] or 0) >= hinted then return hinted end
  local best = 0
  for size, count in pairs(counts) do
    if count >= size and size > best then best = size end
  end
  return best
end

local function select_square(lines, size)
  for start = 1, #lines do
    local selected = {}
    for index = start, math.min(#lines, start + size - 1) do
      if #lines[index].glyphs ~= size then break end
      table.insert(selected, lines[index])
    end
    if #selected == size then return selected end
  end
  local selected = {}
  for _, line in ipairs(lines) do
    if #line.glyphs == size then table.insert(selected, line) end
    if #selected == size then return selected end
  end
  return nil
end

local function capture_scoreboard()
  local snapshot = snapshots.scoreboard()
  if snapshot == nil or snapshot.dimension == nil or #(snapshot.lines or {}) == 0 then return nil end

  local parsed, hinted = {}, nil
  for _, line in ipairs(snapshot.lines) do
    table.insert(parsed, {rawText = line.rawText, glyphs = glyphs_of(line)})
    if hinted == nil then hinted = range_hint(line.rawText) end
  end
  local size = resolve_size(parsed, hinted)
  if size <= 0 then return nil end
  local square = select_square(parsed, size)
  if square == nil then return nil end

  local anchor_row, anchor_column = math.floor(size / 2), math.floor(size / 2)
  for row, line in ipairs(square) do
    for column, glyph in ipairs(line.glyphs) do
      if glyph.symbol == "┼" then
        anchor_row, anchor_column = row - 1, column - 1
      end
    end
  end

  local cells = {}
  for row, line in ipairs(square) do
    for column, glyph in ipairs(line.glyphs) do
      if glyph.symbol ~= "┼" then
        table.insert(cells, {
          x = (column - 1) - anchor_column,
          z = (row - 1) - anchor_row,
          symbol = glyph.symbol,
          color = glyph.color
        })
      end
    end
  end
  return {
    dimension = snapshot.dimension,
    observedAt = snapshot.observedAt,
    coordinateSpace = "relative_to_player",
    mapSize = size,
    anchorRow = anchor_row,
    anchorColumn = anchor_column,
    cells = cells
  }
end

-- 3. Common capability registration / common 能力注册

tv.register_battle_map_source({
  id = CAPABILITY_ID,
  probe = function() return {status = "AVAILABLE", detail = ""} end,
  capture = capture_scoreboard
})

-- 4. Lifecycle / 生命周期
tv.on_enable(function()
  tv.log.info("NodeMC scoreboard Lua adapter enabled")
end)

tv.on_disable(function()
  -- No native objects are owned by this source. / 此数据源不持有外部对象。
end)

tv.on_settings_changed(function(key, value) end)
