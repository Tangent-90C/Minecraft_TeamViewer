for _, capability in ipairs({
  {id = "xaero-worldmap", mod = "xaeroworldmap"},
  {id = "xaero-minimap", mod = "xaerominimap"},
  {id = "xaero-last-seen-minimap", mod = "xaerominimap"}
}) do
  tv.register_unavailable_capability({
    id = capability.id,
    status = environment.loader_id() == "fabric" and "UNSUPPORTED_VERSION" or "NOT_IMPLEMENTED",
    detail = "Xaero Lua adapter is not implemented for " .. environment.loader_id()
        .. " " .. environment.minecraft_version()
  })
end
tv.on_enable(function() end)
tv.on_disable(function() end)
tv.on_settings_changed(function(key, value) end)
