-- Legacy single-entry fallback retained for third-party copies of older manifests.
-- 旧清单复制兼容入口；当前内置清单会选择明确的版本脚本。
for _, key in ipairs({"show_remote_players", "show_map_markers", "show_beacons"}) do
  tv.configure_setting({key = key, visible = false, enabled = false})
end
for _, capability in ipairs({
  "journeymap-players", "journeymap-player-beacons", "journeymap-shared-waypoints"
}) do
  tv.register_unavailable_capability({
    id = capability, status = "UNSUPPORTED_VERSION",
    detail = "No JourneyMap Lua entrypoint matched this runtime"
  })
end
