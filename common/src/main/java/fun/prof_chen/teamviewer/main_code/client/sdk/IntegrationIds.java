package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.Map;

/** Stable IDs shared by real adapters, placeholders, Lua manifests and capability reports. */
public final class IntegrationIds {
    public static final String JOURNEYMAP_PLAYERS = "journeymap-players";
    public static final String JOURNEYMAP_BEACONS = "journeymap-player-beacons";
    public static final String JOURNEYMAP_WAYPOINTS = "journeymap-shared-waypoints";
    public static final String XAERO_WORLDMAP = "xaero-worldmap";
    public static final String XAERO_MINIMAP = "xaero-minimap";
    public static final String NODEMC_BATTLE_MAP = "nodemc-scoreboard-battle-map";
    public static final String SIMMC_BATTLE_MAP = "simmc-native-battle-map";
    public static final String EXAMPLE_REMOTE_PLAYER = "teamviewer-example-remote-player";
    public static final String EXAMPLE_SHARED_WAYPOINT = "teamviewer-example-shared-waypoint";
    public static final String EXAMPLE_BATTLE_MAP = "teamviewer-example-battle-map";

    public static final String PLUGIN_JOURNEYMAP = "teamviewer.journeymap";
    public static final String PLUGIN_XAERO = "teamviewer.xaero";
    public static final String PLUGIN_NODEMC = "teamviewer.nodemc";
    public static final String PLUGIN_SIMMC = "teamviewer.simmc";
    public static final String PLUGIN_EXAMPLE = "teamviewer.example";

    private IntegrationIds() { }

    public static String canonicalize(String id) {
        return id == null ? "" : id.trim();
    }

    public static String pluginIdForCapability(String capabilityId) {
        return switch (canonicalize(capabilityId)) {
            case JOURNEYMAP_PLAYERS, JOURNEYMAP_BEACONS, JOURNEYMAP_WAYPOINTS -> PLUGIN_JOURNEYMAP;
            case XAERO_WORLDMAP, XAERO_MINIMAP -> PLUGIN_XAERO;
            case NODEMC_BATTLE_MAP -> PLUGIN_NODEMC;
            case SIMMC_BATTLE_MAP -> PLUGIN_SIMMC;
            default -> "external";
        };
    }

    public static Map<String, String> expectedRoles() {
        return Map.of(
                JOURNEYMAP_PLAYERS, IntegrationRole.REMOTE_PLAYER.id(),
                JOURNEYMAP_BEACONS, IntegrationRole.REMOTE_PLAYER.id(),
                JOURNEYMAP_WAYPOINTS, IntegrationRole.SHARED_WAYPOINT.id(),
                XAERO_WORLDMAP, IntegrationRole.REMOTE_PLAYER.id(),
                XAERO_MINIMAP, IntegrationRole.SHARED_WAYPOINT.id(),
                NODEMC_BATTLE_MAP, IntegrationRole.BATTLE_MAP_SOURCE.id(),
                SIMMC_BATTLE_MAP, IntegrationRole.BATTLE_MAP_SOURCE.id());
    }
}
