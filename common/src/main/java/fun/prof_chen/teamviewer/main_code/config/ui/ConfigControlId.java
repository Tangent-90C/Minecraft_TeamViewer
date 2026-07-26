package fun.prof_chen.teamviewer.main_code.config.ui;

import java.util.Objects;

/** Stable string control key; plugin controls can carry a plugin ID and setting key. */
public record ConfigControlId(String value) {
    private static final String PLUGIN_PREFIX = "plugin:";
    private static final String SETTING_PREFIX = "setting:";

    public ConfigControlId {
        value = Objects.requireNonNull(value, "value");
    }

    public static ConfigControlId plugin(String pluginId, String action) {
        return new ConfigControlId(PLUGIN_PREFIX + action + ":" + pluginId);
    }

    public static ConfigControlId setting(String pluginId, String key) {
        return new ConfigControlId(SETTING_PREFIX + pluginId + ":" + key);
    }

    public boolean isPluginAction() { return value.startsWith(PLUGIN_PREFIX); }
    public boolean isPluginSetting() { return value.startsWith(SETTING_PREFIX); }

    public String pluginAction() {
        if (!isPluginAction()) return "";
        int separator = value.indexOf(':', PLUGIN_PREFIX.length());
        return separator < 0 ? "" : value.substring(PLUGIN_PREFIX.length(), separator);
    }

    public String pluginId() {
        if (isPluginAction()) {
            int separator = value.indexOf(':', PLUGIN_PREFIX.length());
            return separator < 0 ? "" : value.substring(separator + 1);
        }
        if (isPluginSetting()) {
            int separator = value.indexOf(':', SETTING_PREFIX.length());
            return separator < 0 ? "" : value.substring(SETTING_PREFIX.length(), separator);
        }
        return "";
    }

    public String settingKey() {
        if (!isPluginSetting()) return "";
        int separator = value.indexOf(':', SETTING_PREFIX.length());
        return separator < 0 ? "" : value.substring(separator + 1);
    }

    private static ConfigControlId fixed(String value) { return new ConfigControlId(value); }

    public static final ConfigControlId SERVER_URL = fixed("SERVER_URL");
    public static final ConfigControlId ROOM_CODE = fixed("ROOM_CODE");
    public static final ConfigControlId ALLOW_INSECURE_TLS = fixed("ALLOW_INSECURE_TLS");
    public static final ConfigControlId OPEN_DISPLAY = fixed("OPEN_DISPLAY");
    public static final ConfigControlId OPEN_NETWORK = fixed("OPEN_NETWORK");
    public static final ConfigControlId OPEN_PLUGINS = fixed("OPEN_PLUGINS");
    public static final ConfigControlId SAVE_ROOT = fixed("SAVE_ROOT");
    public static final ConfigControlId AUTO_CONNECT = fixed("AUTO_CONNECT");
    public static final ConfigControlId CONNECT = fixed("CONNECT");
    public static final ConfigControlId DISCONNECT = fixed("DISCONNECT");
    public static final ConfigControlId CONNECTION_STATUS = fixed("CONNECTION_STATUS");
    public static final ConfigControlId SAVE_HINT = fixed("SAVE_HINT");
    public static final ConfigControlId RENDER_DISTANCE = fixed("RENDER_DISTANCE");
    public static final ConfigControlId TRACER_TOP_OFFSET = fixed("TRACER_TOP_OFFSET");
    public static final ConfigControlId SHOW_BOXES = fixed("SHOW_BOXES");
    public static final ConfigControlId SHOW_LINES = fixed("SHOW_LINES");
    public static final ConfigControlId TRACER_START_MODE = fixed("TRACER_START_MODE");
    public static final ConfigControlId XRAY_MARKERS_AND_BOXES = fixed("XRAY_MARKERS_AND_BOXES");
    public static final ConfigControlId OPEN_COLOR = fixed("OPEN_COLOR");
    public static final ConfigControlId OPEN_WAYPOINT = fixed("OPEN_WAYPOINT");
    public static final ConfigControlId SHOW_NETWORK_TRAFFIC_HUD = fixed("SHOW_NETWORK_TRAFFIC_HUD");
    public static final ConfigControlId BOX_COLOR = fixed("BOX_COLOR");
    public static final ConfigControlId LINE_COLOR = fixed("LINE_COLOR");
    public static final ConfigControlId FRIENDLY_TEAM_COLOR = fixed("FRIENDLY_TEAM_COLOR");
    public static final ConfigControlId NEUTRAL_TEAM_COLOR = fixed("NEUTRAL_TEAM_COLOR");
    public static final ConfigControlId ENEMY_TEAM_COLOR = fixed("ENEMY_TEAM_COLOR");
    public static final ConfigControlId WAYPOINT_TIMEOUT = fixed("WAYPOINT_TIMEOUT");
    public static final ConfigControlId LONG_TERM_WAYPOINT_TIMEOUT = fixed("LONG_TERM_WAYPOINT_TIMEOUT");
    public static final ConfigControlId QUICK_MARK_MAX_COUNT = fixed("QUICK_MARK_MAX_COUNT");
    public static final ConfigControlId WAYPOINT_UI_STYLE = fixed("WAYPOINT_UI_STYLE");
    public static final ConfigControlId SHOW_SHARED_WAYPOINTS = fixed("SHOW_SHARED_WAYPOINTS");
    public static final ConfigControlId SHOW_OWN_SHARED_WAYPOINTS = fixed("SHOW_OWN_SHARED_WAYPOINTS");
    public static final ConfigControlId MIDDLE_DOUBLE_CLICK_MARK = fixed("MIDDLE_DOUBLE_CLICK_MARK");
    public static final ConfigControlId MIDDLE_CLICK_CANCEL = fixed("MIDDLE_CLICK_CANCEL");
    public static final ConfigControlId AUTO_CANCEL_ON_ENTITY_DEATH = fixed("AUTO_CANCEL_ON_ENTITY_DEATH");
    public static final ConfigControlId ENABLE_LONG_TERM_WAYPOINT = fixed("ENABLE_LONG_TERM_WAYPOINT");
    public static final ConfigControlId OPEN_WAYPOINT_SHAPE = fixed("OPEN_WAYPOINT_SHAPE");
    public static final ConfigControlId WAYPOINT_BEAM_WIDTH = fixed("WAYPOINT_BEAM_WIDTH");
    public static final ConfigControlId WAYPOINT_BEAM_HEIGHT = fixed("WAYPOINT_BEAM_HEIGHT");
    public static final ConfigControlId TAMPERMONKEY_BEAM_WIDTH = fixed("TAMPERMONKEY_BEAM_WIDTH");
    public static final ConfigControlId TAMPERMONKEY_BEAM_HEIGHT = fixed("TAMPERMONKEY_BEAM_HEIGHT");
    public static final ConfigControlId UPDATE_INTERVAL = fixed("UPDATE_INTERVAL");
    public static final ConfigControlId BATTLE_MAP_UPDATE_INTERVAL = fixed("BATTLE_MAP_UPDATE_INTERVAL");
    public static final ConfigControlId BATTLE_MAP_KEEPALIVE_INTERVAL = fixed("BATTLE_MAP_KEEPALIVE_INTERVAL");
    public static final ConfigControlId BATTLE_MAP_CACHE_RETENTION = fixed("BATTLE_MAP_CACHE_RETENTION");
    public static final ConfigControlId UPLOAD_ENTITIES = fixed("UPLOAD_ENTITIES");
    public static final ConfigControlId UPLOAD_SHARED_WAYPOINTS = fixed("UPLOAD_SHARED_WAYPOINTS");
    public static final ConfigControlId USE_SYSTEM_PROXY = fixed("USE_SYSTEM_PROXY");
    public static final ConfigControlId PREFER_LOCAL_DATA = fixed("PREFER_LOCAL_DATA");
    public static final ConfigControlId BATTLE_MAP_SYNC = fixed("BATTLE_MAP_SYNC");
    public static final ConfigControlId BATTLE_MAP_MODE = fixed("BATTLE_MAP_MODE");
    public static final ConfigControlId BATTLE_MAP_DEBUG = fixed("BATTLE_MAP_DEBUG");
    public static final ConfigControlId OPEN_PACKET_CAPTURE = fixed("OPEN_PACKET_CAPTURE");
    public static final ConfigControlId PACKET_CAPTURE_DESCRIPTION = fixed("PACKET_CAPTURE_DESCRIPTION");
    public static final ConfigControlId PACKET_CAPTURE_STATUS = fixed("PACKET_CAPTURE_STATUS");
    public static final ConfigControlId PACKET_CAPTURE_CURRENT_PATH = fixed("PACKET_CAPTURE_CURRENT_PATH");
    public static final ConfigControlId PACKET_CAPTURE_LAST_PATH = fixed("PACKET_CAPTURE_LAST_PATH");
    public static final ConfigControlId PACKET_CAPTURE_START = fixed("PACKET_CAPTURE_START");
    public static final ConfigControlId PACKET_CAPTURE_STOP = fixed("PACKET_CAPTURE_STOP");
    public static final ConfigControlId PLUGIN_RESCAN = fixed("PLUGIN_RESCAN");
    public static final ConfigControlId PLUGIN_PREVIOUS = fixed("PLUGIN_PREVIOUS");
    public static final ConfigControlId PLUGIN_NEXT = fixed("PLUGIN_NEXT");
    public static final ConfigControlId PLUGIN_PAGE_STATUS = fixed("PLUGIN_PAGE_STATUS");
    public static final ConfigControlId PLUGIN_EMPTY_STATUS = fixed("PLUGIN_EMPTY_STATUS");
    public static final ConfigControlId DISABLED_PLUGIN_EMPTY_STATUS = fixed("DISABLED_PLUGIN_EMPTY_STATUS");
    public static final ConfigControlId DISABLED_PLUGIN_PATH = fixed("DISABLED_PLUGIN_PATH");
    public static final ConfigControlId PLUGIN_OPEN_DISABLED = fixed("PLUGIN_OPEN_DISABLED");
    public static final ConfigControlId DISABLED_PLUGIN_PREVIOUS = fixed("DISABLED_PLUGIN_PREVIOUS");
    public static final ConfigControlId DISABLED_PLUGIN_NEXT = fixed("DISABLED_PLUGIN_NEXT");
    public static final ConfigControlId PLUGIN_GUIDE_OPEN_DIRECTORY = fixed("PLUGIN_GUIDE_OPEN_DIRECTORY");
    public static final ConfigControlId PLUGIN_GUIDE_RETURN_LIST = fixed("PLUGIN_GUIDE_RETURN_LIST");
    public static final ConfigControlId PLUGIN_OPERATION_STATUS = fixed("PLUGIN_OPERATION_STATUS");
    public static final ConfigControlId BACK = fixed("BACK");
}
