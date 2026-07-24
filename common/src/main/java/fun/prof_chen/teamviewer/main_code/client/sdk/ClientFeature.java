package fun.prof_chen.teamviewer.main_code.client.sdk;

/**
 * Complete user-visible client feature contract. Every Minecraft adapter must declare the
 * full set; optional-mod features are implemented by adapters that report unavailable at
 * runtime when the optional mod is absent.
 */
public enum ClientFeature {
    CONNECTION_LIFECYCLE,
    PLAYER_REPORTING,
    ENTITY_REPORTING,
    QUICK_MARK_KEY,
    MIDDLE_DOUBLE_CLICK_MARK,
    MIDDLE_CLICK_CANCEL,
    ENTITY_DEATH_CANCEL,
    PLAYER_BOX_RENDER,
    PLAYER_TRACER_RENDER,
    TEAM_COLOR_RENDER,
    WAYPOINT_BEACON_RENDER,
    WAYPOINT_RING_RENDER,
    WAYPOINT_PIN_RENDER,
    WEB_TACTICAL_PILLAR_RENDER,
    XRAY_RENDER,
    NETWORK_TRAFFIC_HUD,
    PACKET_CAPTURE_HUD,
    LOCAL_MARKED_HUD,
    COMPLETE_CONFIG_UI,
    NODEMC_BATTLE_MAP,
    SIMMC_BATTLE_MAP,
    JOURNEYMAP_REMOTE_BEACON,
    JOURNEYMAP_REMOTE_MARKER,
    JOURNEYMAP_SHARED_WAYPOINT,
    XAERO_REMOTE_MARKER,
    XAERO_SHARED_WAYPOINT
}
