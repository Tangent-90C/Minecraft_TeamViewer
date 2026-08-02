for _, key in ipairs({"show_remote_players", "show_map_markers", "show_beacons"}) do
  tv.configure_setting({key = key, visible = false, enabled = false})
end
for _, id in ipairs({"journeymap-players", "journeymap-player-beacons", "journeymap-shared-waypoints"}) do
  tv.register_unavailable_capability({id = id,
    status = environment.loader_id() == "fabric" and "UNSUPPORTED_VERSION" or "NOT_IMPLEMENTED",
    detail = "JourneyMap Lua adapter is not implemented for " .. environment.loader_id()
        .. " " .. environment.minecraft_version()})
end
tv.on_enable(function() end)
tv.on_disable(function() end)
tv.on_settings_changed(function(key, value) end)
