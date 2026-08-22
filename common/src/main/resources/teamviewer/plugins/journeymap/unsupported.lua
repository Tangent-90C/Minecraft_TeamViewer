for _, key in ipairs({"show_remote_players", "show_last_seen_players", "show_online_map_markers",
    "show_online_world_beacons", "show_offline_map_markers", "show_offline_world_beacons"}) do
  tv.configure_setting({key = key, visible = false, enabled = false})
end
for _, role in ipairs({"online", "offline", "player_reports", "web_reports", "other_shared"}) do
  for _, suffix in ipairs({"render_world", "rotating_beam", "static_beam", "max_distance"}) do
    tv.configure_setting({key = role .. "_" .. suffix, visible = false, enabled = false})
  end
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
