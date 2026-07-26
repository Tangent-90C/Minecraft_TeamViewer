local status = environment.loader_id() == "fabric" and "UNSUPPORTED_VERSION" or "NOT_IMPLEMENTED"
tv.register_unavailable_capability({
  id = "simmc-native-battle-map",
  status = status,
  detail = "SimMC Lua adapter is not implemented for " .. environment.loader_id()
      .. " " .. environment.minecraft_version()
})
tv.on_enable(function() end)
tv.on_disable(function() end)
tv.on_settings_changed(function(key, value) end)
