package fun.prof_chen.teamviewer.main_code.config.ui;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRole;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.plugin.DisabledPluginSnapshot;
import fun.prof_chen.teamviewer.main_code.plugin.PluginFileOperationResult;
import fun.prof_chen.teamviewer.main_code.plugin.PluginManifest;
import fun.prof_chen.teamviewer.main_code.plugin.PluginSnapshot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId.*;

/**
 * The complete platform-neutral TeamViewRelay configuration UI state machine.
 * Minecraft adapters render the immutable page view and forward edits/actions here.
 */
public final class ConfigUiSession implements ConfigUiController {
    private static final int HEIGHT = 20;
    private static final int LABEL_HEIGHT = 12;
    private static final int LABEL_SPACING = 12;

    private final ClientControlGateway control;
    private final Config config;
    private final Map<ConfigControlId, String> textValues = new HashMap<>();
    private String originalUrl;
    private String originalRoomCode;
    private boolean originalAutoConnect;
    private boolean originalAllowInsecureTls;
    private boolean allowInsecureTls;
    private String selectedPluginId;
    private String selectedDisabledPluginId;
    private String pendingDeleteDisabledPluginId;
    private PluginFileOperationResult lastPluginOperation;
    private Path copiedPluginPath;
    private boolean openDirectoryFailed;
    private int pluginListPage;
    private int disabledPluginListPage;

    public ConfigUiSession(ClientControlGateway control) {
        this.control = Objects.requireNonNull(control, "control");
        this.config = Objects.requireNonNull(control.getConfig(), "config");
        captureRootBaseline();
        initializeTextValues();
    }

    public ConfigPageView page(ConfigPageId pageId, int width, int height) {
        return switch (pageId) {
            case ROOT -> rootPage(width, height);
            case DISPLAY -> displayPage(width, height);
            case NETWORK -> networkPage(width, height);
            case COLOR -> colorPage(width, height);
            case WAYPOINT -> waypointPage(width, height);
            case WAYPOINT_SHAPE -> waypointShapePage(width, height);
            case PACKET_CAPTURE -> packetCapturePage(width, height);
            case PLUGINS -> pluginsPage(width, height);
            case PLUGIN_DETAIL -> pluginDetailPage(width, height);
            case PLUGIN_COPY_GUIDE -> pluginCopyGuidePage(width, height);
            case DISABLED_PLUGINS -> disabledPluginsPage(width, height);
            case DISABLED_PLUGIN_DETAIL -> disabledPluginDetailPage(width, height);
            case PLUGIN_DELETE_CONFIRM -> pluginDeleteConfirmPage(width, height);
        };
    }

    public void setText(ConfigControlId id, String value) {
        if (id != null && value != null) {
            textValues.put(id, value);
        }
    }

    public void setChecked(ConfigControlId id, boolean checked) {
        if (ALLOW_INSECURE_TLS.equals(id)) {
            allowInsecureTls = checked;
        } else if (id != null && id.isPluginSetting()) {
            control.setIntegrationPluginSetting(id.pluginId(), id.settingKey(), checked);
        }
    }

    public ConfigUiAction activate(ConfigPageId currentPage, ConfigControlId id) {
        if (id == null) {
            return ConfigUiAction.stay();
        }
        if (id.isPluginAction()) return activatePluginAction(id);
        if (id.isPluginSetting()) return activatePluginSetting(id);
        return switch (id.value()) {
            case "OPEN_DISPLAY" -> ConfigUiAction.open(ConfigPageId.DISPLAY);
            case "OPEN_NETWORK" -> ConfigUiAction.open(ConfigPageId.NETWORK);
            case "OPEN_PLUGINS" -> ConfigUiAction.open(ConfigPageId.PLUGINS);
            case "OPEN_COLOR" -> ConfigUiAction.open(ConfigPageId.COLOR);
            case "OPEN_WAYPOINT" -> ConfigUiAction.open(ConfigPageId.WAYPOINT);
            case "OPEN_WAYPOINT_SHAPE" -> ConfigUiAction.open(ConfigPageId.WAYPOINT_SHAPE);
            case "OPEN_PACKET_CAPTURE" -> ConfigUiAction.open(ConfigPageId.PACKET_CAPTURE);
            case "BACK" -> {
                applyPageFields(currentPage);
                yield ConfigUiAction.closeToParent();
            }
            case "SAVE_ROOT" -> {
                saveRoot();
                yield ConfigUiAction.closeToParent();
            }
            case "AUTO_CONNECT" -> toggleAutoConnect();
            case "CONNECT" -> connect();
            case "DISCONNECT" -> disconnect();
            case "SHOW_BOXES" -> toggle(config.isShowBoxes(), config::setShowBoxes);
            case "SHOW_LINES" -> toggle(config.isShowLines(), config::setShowLines);
            case "TRACER_START_MODE" -> cycleTracerMode();
            case "XRAY_MARKERS_AND_BOXES" -> toggle(config.isXrayMarkersAndBoxes(), config::setXrayMarkersAndBoxes);
            case "SHOW_NETWORK_TRAFFIC_HUD" -> toggle(config.isShowNetworkTrafficHud(), config::setShowNetworkTrafficHud);
            case "SHOW_SHARED_WAYPOINTS" -> toggle(config.isShowSharedWaypoints(), config::setShowSharedWaypoints);
            case "SHOW_OWN_SHARED_WAYPOINTS" -> toggle(config.isShowOwnSharedWaypointsOnMinimap(), config::setShowOwnSharedWaypointsOnMinimap);
            case "MIDDLE_DOUBLE_CLICK_MARK" -> toggle(config.isEnableMiddleDoubleClickMark(), config::setEnableMiddleDoubleClickMark);
            case "MIDDLE_CLICK_CANCEL" -> toggle(config.isEnableMiddleClickCancelWaypoint(), config::setEnableMiddleClickCancelWaypoint);
            case "AUTO_CANCEL_ON_ENTITY_DEATH" -> toggle(config.isAutoCancelWaypointOnEntityDeath(), config::setAutoCancelWaypointOnEntityDeath);
            case "ENABLE_LONG_TERM_WAYPOINT" -> toggle(config.isEnableLongTermWaypoint(), config::setEnableLongTermWaypoint);
            case "WAYPOINT_UI_STYLE" -> cycleWaypointStyle();
            case "UPLOAD_ENTITIES" -> toggle(config.isUploadEntities(), config::setUploadEntities);
            case "UPLOAD_SHARED_WAYPOINTS" -> toggle(config.isUploadSharedWaypoints(), config::setUploadSharedWaypoints);
            case "USE_SYSTEM_PROXY" -> toggle(config.isUseSystemProxy(), config::setUseSystemProxy);
            case "PREFER_LOCAL_DATA" -> toggle(config.isPreferLocalDataForRender(), config::setPreferLocalDataForRender);
            case "BATTLE_MAP_SYNC" -> toggle(config.isBattleMapSyncEnabled(), config::setBattleMapSyncEnabled);
            case "BATTLE_MAP_MODE" -> cycleBattleMapMode();
            case "BATTLE_MAP_DEBUG" -> toggle(config.isBattleMapDebugEnabled(), config::setBattleMapDebugEnabled);
            case "PACKET_CAPTURE_START" -> startPacketCapture();
            case "PACKET_CAPTURE_STOP" -> stopPacketCapture();
            case "PLUGIN_RESCAN" -> {
                control.rescanIntegrationPlugins();
                yield ConfigUiAction.stay();
            }
            case "PLUGIN_PREVIOUS" -> {
                pluginListPage = Math.max(0, pluginListPage - 1);
                yield ConfigUiAction.reload();
            }
            case "PLUGIN_NEXT" -> {
                pluginListPage++;
                yield ConfigUiAction.reload();
            }
            case "PLUGIN_OPEN_DISABLED" -> ConfigUiAction.open(ConfigPageId.DISABLED_PLUGINS);
            case "DISABLED_PLUGIN_PREVIOUS" -> {
                disabledPluginListPage = Math.max(0, disabledPluginListPage - 1);
                yield ConfigUiAction.reload();
            }
            case "DISABLED_PLUGIN_NEXT" -> {
                disabledPluginListPage++;
                yield ConfigUiAction.reload();
            }
            case "PLUGIN_GUIDE_OPEN_DIRECTORY" -> {
                openDirectoryFailed = copiedPluginPath == null
                        || !control.openIntegrationPluginDirectory(copiedPluginPath);
                yield ConfigUiAction.stay();
            }
            case "PLUGIN_GUIDE_RETURN_LIST" -> ConfigUiAction.open(ConfigPageId.PLUGINS);
            default -> ConfigUiAction.stay();
        };
    }

    public ConfigUiAction close(ConfigPageId pageId) {
        if (pageId == ConfigPageId.ROOT) {
            cancelRoot();
        } else {
            applyPageFields(pageId);
        }
        return ConfigUiAction.closeToParent();
    }

    public void saveRoot() {
        String url = value(SERVER_URL).trim();
        if (!url.isEmpty()) {
            config.setServerURL(url);
        }
        config.setRoomCode(value(ROOM_CODE).trim());
        config.setAllowInsecureTls(allowInsecureTls);
        config.save();
        captureRootBaseline();
    }

    public void cancelRoot() {
        config.setServerURL(originalUrl);
        config.setRoomCode(originalRoomCode);
        config.setAutoConnectOnMultiplayerJoin(originalAutoConnect);
        config.setAllowInsecureTls(originalAllowInsecureTls);
        allowInsecureTls = originalAllowInsecureTls;
        textValues.put(SERVER_URL, originalUrl);
        textValues.put(ROOM_CODE, originalRoomCode);
    }

    public boolean hasUnsavedRootChanges() {
        return !value(SERVER_URL).trim().equals(originalUrl)
                || !value(ROOM_CODE).trim().equals(originalRoomCode)
                || config.isAutoConnectOnMultiplayerJoin() != originalAutoConnect
                || allowInsecureTls != originalAllowInsecureTls;
    }

    private void captureRootBaseline() {
        originalUrl = config.getServerURL();
        originalRoomCode = config.getRoomCode();
        originalAutoConnect = config.isAutoConnectOnMultiplayerJoin();
        originalAllowInsecureTls = config.isAllowInsecureTls();
        allowInsecureTls = originalAllowInsecureTls;
    }

    private void initializeTextValues() {
        textValues.put(SERVER_URL, config.getServerURL());
        textValues.put(ROOM_CODE, config.getRoomCode());
        textValues.put(RENDER_DISTANCE, String.valueOf(config.getRenderDistance()));
        textValues.put(TRACER_TOP_OFFSET, String.valueOf(config.getTracerTopOffset()));
        textValues.put(BOX_COLOR, color(config.getBoxColor()));
        textValues.put(LINE_COLOR, color(config.getLineColor()));
        textValues.put(FRIENDLY_TEAM_COLOR, color(config.getFriendlyTeamColor()));
        textValues.put(NEUTRAL_TEAM_COLOR, color(config.getNeutralTeamColor()));
        textValues.put(ENEMY_TEAM_COLOR, color(config.getEnemyTeamColor()));
        textValues.put(WAYPOINT_TIMEOUT, String.valueOf(config.getWaypointTimeoutSeconds()));
        textValues.put(LONG_TERM_WAYPOINT_TIMEOUT, String.valueOf(config.getLongTermWaypointTimeoutSeconds()));
        textValues.put(QUICK_MARK_MAX_COUNT, String.valueOf(config.getMaxQuickMarkCount()));
        textValues.put(WAYPOINT_BEAM_WIDTH, String.valueOf(config.getWaypointBeaconBeamWidth()));
        textValues.put(WAYPOINT_BEAM_HEIGHT, String.valueOf(config.getWaypointBeaconBeamHeight()));
        textValues.put(TAMPERMONKEY_BEAM_WIDTH, String.valueOf(config.getTampermonkeyBeamWidth()));
        textValues.put(TAMPERMONKEY_BEAM_HEIGHT, String.valueOf(config.getTampermonkeyBeamHeight()));
        textValues.put(UPDATE_INTERVAL, String.valueOf(config.getUpdateInterval()));
        textValues.put(BATTLE_MAP_UPDATE_INTERVAL, String.valueOf(config.getBattleMapUpdateIntervalTicks()));
        textValues.put(BATTLE_MAP_KEEPALIVE_INTERVAL, String.valueOf(config.getBattleMapKeepaliveIntervalSeconds()));
        textValues.put(BATTLE_MAP_CACHE_RETENTION, String.valueOf(config.getBattleMapCacheRetentionSeconds()));
    }

    private ConfigPageView rootPage(int width, int height) {
        int componentWidth = 200;
        int totalHeight = 30 * 5 + 25 * 4;
        int start = (height - totalHeight) / 2;
        int y = start + 30;
        int x = (width - componentWidth) / 2;
        int half = (componentWidth - 4) / 2;
        List<ConfigControlView> controls = new ArrayList<>();
        controls.add(field(SERVER_URL, x, y, componentWidth, "screen.mc_teamviewer.config.url",
                "screen.mc_teamviewer.config.url_hint", null, 2048));
        y += 30;
        controls.add(field(ROOM_CODE, x, y, half, "screen.mc_teamviewer.config.room_code",
                "screen.mc_teamviewer.config.room_code_hint", null, 64));
        controls.add(ConfigControlView.checkbox(ALLOW_INSECURE_TLS, new UiRect(x + half + 4, y, half, HEIGHT),
                tr("screen.mc_teamviewer.config.allow_insecure_tls"), tr("screen.mc_teamviewer.config.allow_insecure_tls.tooltip"),
                allowInsecureTls));
        y += 30;
        controls.add(button(OPEN_DISPLAY, x, y, 99, "screen.mc_teamviewer.config.display_settings", null));
        controls.add(button(OPEN_NETWORK, x + 101, y, 99, "screen.mc_teamviewer.config.network_settings", null));
        y += 25;
        controls.add(button(OPEN_PLUGINS, x, y, componentWidth, "screen.mc_teamviewer.config.integration_plugins", null));
        y += 25;
        ConfigControlView save = button(SAVE_ROOT, x, y, half, "screen.mc_teamviewer.config.save_network_settings", null);
        if (hasUnsavedRootChanges()) {
            save = new ConfigControlView(save.id(), save.kind(), save.bounds(), save.labelBounds(), save.label(), save.hint(),
                    save.tooltip(), save.value(), save.maxLength(), save.checked(), save.active(), save.visible(), 0x55FF55,
                    save.alignment());
        }
        controls.add(save);
        controls.add(ConfigControlView.button(AUTO_CONNECT, new UiRect(x + half + 4, y, half, HEIGHT),
                UiText.toggle("screen.mc_teamviewer.config.auto_connect_compact", config.isAutoConnectOnMultiplayerJoin()), null, true));
        y += 25;
        controls.add(ConfigControlView.button(CONNECT, new UiRect(x, y, 99, HEIGHT),
                tr(control.isEnabled() ? "screen.mc_teamviewer.config.reconnect" : "screen.mc_teamviewer.config.connect"), null, true));
        controls.add(ConfigControlView.button(DISCONNECT, new UiRect(x + 101, y, 99, HEIGHT),
                tr("screen.mc_teamviewer.config.disconnect"), null,
                control.isEnabled() || control.getNetworkManager().isConnected()));
        y += 25;
        NetworkManager.ConnectionStage stage = control.getNetworkManager().getConnectionStage();
        UiText status = stage == NetworkManager.ConnectionStage.FAILED
                ? tr("screen.mc_teamviewer.config.connection_failed_short")
                : UiText.translatable("screen.mc_teamviewer.config.connection_status", tr(statusKey(stage)));
        controls.add(ConfigControlView.text(CONNECTION_STATUS, new UiRect(x, y, componentWidth, HEIGHT), status,
                stage == NetworkManager.ConnectionStage.FAILED
                        ? (isBlank(control.getNetworkManager().getLastConnectionError())
                        ? tr("screen.mc_teamviewer.config.unknown_error")
                        : UiText.literal(control.getNetworkManager().getLastConnectionError())) : null,
                statusColor(stage), true, ConfigControlView.TextAlignment.CENTER));
        y += 30;
        controls.add(ConfigControlView.text(SAVE_HINT, new UiRect(x, y, componentWidth, HEIGHT),
                tr("screen.mc_teamviewer.config.save_required_hint"), null, 0xFFAA00,
                hasUnsavedRootChanges(), ConfigControlView.TextAlignment.CENTER));
        return new ConfigPageView(ConfigPageId.ROOT, tr("screen.mc_teamviewer.config.title"), start - 30, controls);
    }

    private ConfigPageView displayPage(int width, int height) {
        int start = (height - (30 * 3 + 25 * 6)) / 2;
        int y = start + 30;
        int left = (width - 346) / 2;
        int right = left + 176;
        List<ConfigControlView> c = new ArrayList<>();
        c.add(field(RENDER_DISTANCE, left, y, 170, "screen.mc_teamviewer.config.render_distance",
                "screen.mc_teamviewer.config.render_distance_hint", "screen.mc_teamviewer.config.render_distance.tooltip", 100));
        c.add(field(TRACER_TOP_OFFSET, right, y, 170, "screen.mc_teamviewer.config.tracer_top_offset",
                "screen.mc_teamviewer.config.tracer_top_offset_hint", "screen.mc_teamviewer.config.tracer_top_offset.tooltip", 10));
        y += 30;
        c.add(toggleButtonWithTooltip(SHOW_BOXES, left, y, 170, "screen.mc_teamviewer.config.show_boxes", config.isShowBoxes()));
        c.add(toggleButtonWithTooltip(SHOW_LINES, right, y, 170, "screen.mc_teamviewer.config.show_tracking_lines", config.isShowLines()));
        y += 25;
        String tracerMode = config.isTracerStartTop()
                ? "screen.mc_teamviewer.config.tracer_start_mode.top" : "screen.mc_teamviewer.config.tracer_start_mode.crosshair";
        c.add(ConfigControlView.button(TRACER_START_MODE, new UiRect(left, y, 170, HEIGHT),
                tr("screen.mc_teamviewer.config.tracer_start_mode").append(": ").append(""),
                tr("screen.mc_teamviewer.config.tracer_start_mode.tooltip"), true));
        // The mode suffix is a translated argument, so use a standard translatable label for adapters.
        c.set(c.size() - 1, ConfigControlView.button(TRACER_START_MODE, new UiRect(left, y, 170, HEIGHT),
                UiText.translatable("screen.mc_teamviewer.config.value", tr("screen.mc_teamviewer.config.tracer_start_mode"), tr(tracerMode)),
                tr("screen.mc_teamviewer.config.tracer_start_mode.tooltip"), true));
        c.add(toggleButtonWithTooltip(XRAY_MARKERS_AND_BOXES, right, y, 170, "screen.mc_teamviewer.config.xray_markers_and_boxes", config.isXrayMarkersAndBoxes()));
        y += 25;
        c.add(button(OPEN_COLOR, left, y, 170, "screen.mc_teamviewer.config.color_settings", "screen.mc_teamviewer.config.color_settings.tooltip"));
        c.add(button(OPEN_WAYPOINT, right, y, 170, "screen.mc_teamviewer.config.waypoint_settings", "screen.mc_teamviewer.config.waypoint_settings.tooltip"));
        y += 25;
        c.add(toggleButtonWithTooltip(SHOW_NETWORK_TRAFFIC_HUD, left, y, 170, "screen.mc_teamviewer.config.show_network_traffic_hud", config.isShowNetworkTrafficHud()));
        y += 25;
        c.add(button(BACK, left, y, 346, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.DISPLAY, tr("screen.mc_teamviewer.display_config.title"), start - 30, c);
    }

    private ConfigPageView networkPage(int width, int height) {
        int column = Math.max(150, Math.min(200, (width - 80 - 8) / 2));
        int left = (width - column * 2 - 8) / 2;
        int right = left + column + 8;
        int start = (height - (30 * 3 + 25 * 5)) / 2;
        int y = start + 30;
        List<ConfigControlView> c = new ArrayList<>();
        c.add(field(UPDATE_INTERVAL, left, y, column, "screen.mc_teamviewer.config.update_interval",
                "screen.mc_teamviewer.config.update_interval_hint", null, 5));
        c.add(field(BATTLE_MAP_UPDATE_INTERVAL, right, y, column, "screen.mc_teamviewer.config.battle_map_update_interval",
                "screen.mc_teamviewer.config.battle_map_update_interval_hint", null, 5));
        y += 30;
        c.add(field(BATTLE_MAP_KEEPALIVE_INTERVAL, left, y, column, "screen.mc_teamviewer.config.battle_map_keepalive_interval",
                "screen.mc_teamviewer.config.battle_map_keepalive_interval_hint", null, 5));
        c.add(field(BATTLE_MAP_CACHE_RETENTION, right, y, column, "screen.mc_teamviewer.config.battle_map_cache_retention",
                "screen.mc_teamviewer.config.battle_map_cache_retention_hint", null, 6));
        y += 30;
        c.add(toggleButton(UPLOAD_ENTITIES, left, y, column, "screen.mc_teamviewer.config.upload_entities", config.isUploadEntities()));
        c.add(toggleButton(UPLOAD_SHARED_WAYPOINTS, right, y, column, "screen.mc_teamviewer.config.upload_shared_waypoints", config.isUploadSharedWaypoints()));
        y += 25;
        c.add(toggleButton(PREFER_LOCAL_DATA, left, y, column, "screen.mc_teamviewer.config.prefer_local_data_for_rendering", config.isPreferLocalDataForRender()));
        c.add(toggleButton(USE_SYSTEM_PROXY, right, y, column, "screen.mc_teamviewer.config.use_system_proxy", config.isUseSystemProxy()));
        y += 25;
        c.add(toggleButton(BATTLE_MAP_SYNC, left, y, column, "screen.mc_teamviewer.config.battle_map_sync", config.isBattleMapSyncEnabled()));
        c.add(ConfigControlView.button(BATTLE_MAP_MODE, new UiRect(right, y, column, HEIGHT),
                UiText.translatable("screen.mc_teamviewer.config.value",
                        tr("screen.mc_teamviewer.config.battle_map_source"), battleMapSourceLabel()),
                battleMapSourceTooltip(), true));
        y += 25;
        c.add(toggleButton(BATTLE_MAP_DEBUG, left, y, column, "screen.mc_teamviewer.config.battle_map_debug", config.isBattleMapDebugEnabled()));
        c.add(ConfigControlView.button(OPEN_PACKET_CAPTURE, new UiRect(right, y, column, HEIGHT),
                tr("screen.mc_teamviewer.config.packet_capture_page").append(
                        control.getNetworkManager().isPacketDumpCaptureActive() ? " [RUN]" : " [IDLE]"), null, true));
        y += 25;
        c.add(button(BACK, left, y, column * 2 + 8, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.NETWORK, tr("screen.mc_teamviewer.network_config.title"), start - 30, c);
    }

    private ConfigPageView waypointPage(int width, int height) {
        int start = (height - (30 * 3 + 25 * 6)) / 2;
        int y = start + 30;
        int left = (width - 346) / 2;
        int right = left + 176;
        List<ConfigControlView> c = new ArrayList<>();
        c.add(field(WAYPOINT_TIMEOUT, left, y, 170, "screen.mc_teamviewer.config.waypoint_timeout",
                "screen.mc_teamviewer.config.waypoint_timeout_hint", "screen.mc_teamviewer.config.waypoint_timeout.tooltip", 10));
        c.add(field(LONG_TERM_WAYPOINT_TIMEOUT, right, y, 170, "screen.mc_teamviewer.config.long_term_waypoint_timeout",
                "screen.mc_teamviewer.config.long_term_waypoint_timeout_hint", "screen.mc_teamviewer.config.long_term_waypoint_timeout.tooltip", 10));
        y += 30;
        c.add(field(QUICK_MARK_MAX_COUNT, left, y, 170, "screen.mc_teamviewer.config.quick_mark_max_count",
                "screen.mc_teamviewer.config.quick_mark_max_count_hint", "screen.mc_teamviewer.config.quick_mark_max_count.tooltip", 10));
        String styleKey = "screen.mc_teamviewer.config.waypoint_ui_style." + config.getWaypointUiStyle();
        c.add(ConfigControlView.button(WAYPOINT_UI_STYLE, new UiRect(right, y, 170, HEIGHT),
                UiText.translatable("screen.mc_teamviewer.config.value", tr("screen.mc_teamviewer.config.waypoint_ui_style"), tr(styleKey)),
                tr("screen.mc_teamviewer.config.waypoint_ui_style.tooltip"), true));
        y += 30;
        c.add(toggleButtonWithTooltip(SHOW_SHARED_WAYPOINTS, left, y, 170, "screen.mc_teamviewer.config.show_shared_waypoints", config.isShowSharedWaypoints()));
        c.add(toggleButtonWithTooltip(SHOW_OWN_SHARED_WAYPOINTS, right, y, 170, "screen.mc_teamviewer.config.show_own_shared_waypoints_on_minimap", config.isShowOwnSharedWaypointsOnMinimap()));
        y += 25;
        c.add(toggleButtonWithTooltip(MIDDLE_DOUBLE_CLICK_MARK, left, y, 170, "screen.mc_teamviewer.config.middle_double_click_mark", config.isEnableMiddleDoubleClickMark()));
        c.add(toggleButtonWithTooltip(MIDDLE_CLICK_CANCEL, right, y, 170, "screen.mc_teamviewer.config.middle_click_cancel_waypoint", config.isEnableMiddleClickCancelWaypoint()));
        y += 25;
        c.add(toggleButtonWithTooltip(ENABLE_LONG_TERM_WAYPOINT, left, y, 170, "screen.mc_teamviewer.config.enable_long_term_waypoint", config.isEnableLongTermWaypoint()));
        c.add(toggleButtonWithTooltip(AUTO_CANCEL_ON_ENTITY_DEATH, right, y, 170, "screen.mc_teamviewer.config.auto_cancel_waypoint_on_entity_death", config.isAutoCancelWaypointOnEntityDeath()));
        y += 25;
        c.add(button(OPEN_WAYPOINT_SHAPE, left, y, 346, "screen.mc_teamviewer.config.waypoint_shape_settings", "screen.mc_teamviewer.config.waypoint_shape_settings.tooltip"));
        y += 25;
        c.add(button(BACK, left, y, 346, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.WAYPOINT, tr("screen.mc_teamviewer.waypoint_config.title"), start - 30, c);
    }

    private ConfigPageView waypointShapePage(int width, int height) {
        int start = (height - (30 * 3 + 25)) / 2;
        int y = start + 30;
        int left = (width - 346) / 2;
        int right = left + 176;
        List<ConfigControlView> c = new ArrayList<>();
        c.add(field(WAYPOINT_BEAM_WIDTH, left, y, 170, "screen.mc_teamviewer.config.waypoint_beacon_beam_width",
                "screen.mc_teamviewer.config.waypoint_beacon_beam_width_hint", "screen.mc_teamviewer.config.waypoint_beacon_beam_width.tooltip", 10));
        c.add(field(WAYPOINT_BEAM_HEIGHT, right, y, 170, "screen.mc_teamviewer.config.waypoint_beacon_beam_height",
                "screen.mc_teamviewer.config.waypoint_beacon_beam_height_hint", "screen.mc_teamviewer.config.waypoint_beacon_beam_height.tooltip", 10));
        y += 30;
        c.add(field(TAMPERMONKEY_BEAM_WIDTH, left, y, 170, "screen.mc_teamviewer.config.tampermonkey_beam_width",
                "screen.mc_teamviewer.config.tampermonkey_beam_width_hint", "screen.mc_teamviewer.config.tampermonkey_beam_width.tooltip", 10));
        c.add(field(TAMPERMONKEY_BEAM_HEIGHT, right, y, 170, "screen.mc_teamviewer.config.tampermonkey_beam_height",
                "screen.mc_teamviewer.config.tampermonkey_beam_height_hint", "screen.mc_teamviewer.config.tampermonkey_beam_height.tooltip", 10));
        y += 30;
        c.add(button(BACK, left, y, 346, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.WAYPOINT_SHAPE, tr("screen.mc_teamviewer.waypoint_shape_config.title"), start - 30, c);
    }

    private ConfigPageView colorPage(int width, int height) {
        int start = (height - 30 * 7) / 2;
        int y = start + 30;
        int x = (width - 200) / 2;
        List<ConfigControlView> c = new ArrayList<>();
        c.add(field(BOX_COLOR, x, y, 200, "screen.mc_teamviewer.color_config.box_color", null, null, 16));
        y += 30;
        c.add(field(LINE_COLOR, x, y, 200, "screen.mc_teamviewer.color_config.line_color", null, null, 16));
        y += 30;
        c.add(field(FRIENDLY_TEAM_COLOR, x, y, 200, "screen.mc_teamviewer.color_config.friendly_team_color", null, null, 16));
        y += 30;
        c.add(field(NEUTRAL_TEAM_COLOR, x, y, 200, "screen.mc_teamviewer.color_config.neutral_team_color", null, null, 16));
        y += 30;
        c.add(field(ENEMY_TEAM_COLOR, x, y, 200, "screen.mc_teamviewer.color_config.enemy_team_color", null, null, 16));
        y += 30;
        c.add(button(BACK, x, y, 200, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.COLOR, tr("screen.mc_teamviewer.color_config.title"), start - 30, c);
    }

    private ConfigPageView packetCapturePage(int width, int height) {
        int total = 18 * 5 + 28 * 3;
        int start = (height - total) / 2;
        int x = (width - 240) / 2;
        NetworkManager network = control.getNetworkManager();
        boolean active = network.isPacketDumpCaptureActive();
        String current = network.getPacketDumpCurrentPath();
        String last = network.getPacketDumpLastSavedPath();
        List<ConfigControlView> c = new ArrayList<>();
        c.add(ConfigControlView.text(PACKET_CAPTURE_DESCRIPTION, new UiRect(x, start, 240, 12),
                tr("screen.mc_teamviewer.packet_capture.description"), null, 0xE0E0E0, true, ConfigControlView.TextAlignment.CENTER));
        UiText status = !active ? tr("screen.mc_teamviewer.packet_capture.status_idle")
                : isBlank(current) ? tr("screen.mc_teamviewer.packet_capture.status_waiting")
                : tr("screen.mc_teamviewer.packet_capture.status_running");
        c.add(ConfigControlView.text(PACKET_CAPTURE_STATUS, new UiRect(x, start + 18, 240, 12),
                status, null, 0xFFD166, true, ConfigControlView.TextAlignment.CENTER));
        c.add(ConfigControlView.text(PACKET_CAPTURE_CURRENT_PATH, new UiRect(x, start + 36, 240, 12),
                isBlank(current) ? tr("screen.mc_teamviewer.packet_capture.current_file_empty")
                        : UiText.translatable("screen.mc_teamviewer.packet_capture.current_file", UiText.literal(abbreviatePath(current))),
                isBlank(current) ? null : UiText.literal(current), 0xA7F3D0, true, ConfigControlView.TextAlignment.CENTER));
        c.add(ConfigControlView.text(PACKET_CAPTURE_LAST_PATH, new UiRect(x, start + 54, 240, 12),
                isBlank(last) ? tr("screen.mc_teamviewer.packet_capture.last_file_empty")
                        : UiText.translatable("screen.mc_teamviewer.packet_capture.last_file", UiText.literal(abbreviatePath(last))),
                isBlank(last) ? null : UiText.literal(last), 0x93C5FD, true, ConfigControlView.TextAlignment.CENTER));
        int y = start + 72;
        c.add(ConfigControlView.button(PACKET_CAPTURE_START, new UiRect(x, y, 240, HEIGHT),
                tr("screen.mc_teamviewer.packet_capture.start"), null, !active));
        y += 28;
        c.add(ConfigControlView.button(PACKET_CAPTURE_STOP, new UiRect(x, y, 240, HEIGHT),
                tr("screen.mc_teamviewer.packet_capture.stop"), null, active));
        y += 28;
        c.add(button(BACK, x, y, 240, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.PACKET_CAPTURE, tr("screen.mc_teamviewer.packet_capture.title"), start - 24, c);
    }

    private ConfigPageView pluginsPage(int width, int height) {
        List<PluginSnapshot> plugins = control.getIntegrationPlugins();
        int pageSize = Math.max(4, Math.min(8, (height - 175) / 24));
        int pageCount = Math.max(1, (plugins.size() + pageSize - 1) / pageSize);
        pluginListPage = Math.min(pluginListPage, pageCount - 1);
        int startIndex = pluginListPage * pageSize;
        int endIndex = Math.min(plugins.size(), startIndex + pageSize);
        int x = (width - 420) / 2;
        int y = 54;
        List<ConfigControlView> controls = new ArrayList<>();
        for (int index = startIndex; index < endIndex; index++) {
            PluginSnapshot plugin = plugins.get(index);
            long available = plugin.capabilities().stream()
                    .filter(capability -> capability.status() == IntegrationSupportStatus.AVAILABLE)
                    .count();
            controls.add(ConfigControlView.button(ConfigControlId.plugin(plugin.id(), "open"),
                    new UiRect(x, y, 420, HEIGHT),
                    t("screen.mc_teamviewer.integration_plugin.list_entry",
                            tr(plugin.enabled()
                                    ? "screen.mc_teamviewer.integration_plugin.enabled_short"
                                    : "screen.mc_teamviewer.integration_plugin.disabled_short"),
                            pluginName(plugin), runtimeStatusText(plugin.runtimeStatus()),
                            UiText.literal(available + "/" + plugin.capabilities().size())),
                    pluginDiagnostic(plugin), true));
            y += 24;
        }
        if (plugins.isEmpty()) {
            controls.add(ConfigControlView.text(PLUGIN_EMPTY_STATUS, new UiRect(x, y, 420, HEIGHT),
                    tr("screen.mc_teamviewer.integration_plugin.none"), null, 0xFFAA00, true,
                    ConfigControlView.TextAlignment.CENTER));
            y += 24;
        }
        controls.add(ConfigControlView.button(PLUGIN_PREVIOUS, new UiRect(x, y, 90, HEIGHT),
                UiText.literal("<"), null, pluginListPage > 0));
        controls.add(ConfigControlView.text(PLUGIN_PAGE_STATUS, new UiRect(x + 94, y, 232, HEIGHT),
                UiText.literal((pluginListPage + 1) + " / " + pageCount), null, 0xFFFFFF, true,
                ConfigControlView.TextAlignment.CENTER));
        controls.add(ConfigControlView.button(PLUGIN_NEXT, new UiRect(x + 330, y, 90, HEIGHT),
                UiText.literal(">"), null, pluginListPage + 1 < pageCount));
        y += 26;
        controls.add(ConfigControlView.button(PLUGIN_RESCAN, new UiRect(x, y, 132, HEIGHT),
                tr("screen.mc_teamviewer.integration_plugin.rescan"), null, true));
        controls.add(ConfigControlView.button(PLUGIN_OPEN_DISABLED, new UiRect(x + 144, y, 132, HEIGHT),
                tr("screen.mc_teamviewer.integration_plugin.disabled_packages"), null, true));
        controls.add(button(BACK, x + 288, y, 132, "screen.mc_teamviewer.config.back", null));
        if (lastPluginOperation != null) {
            y += 24;
            controls.add(ConfigControlView.text(PLUGIN_OPERATION_STATUS, new UiRect(x, y, 420, HEIGHT),
                    operationResultText(lastPluginOperation), operationDetail(lastPluginOperation),
                    lastPluginOperation.succeeded() ? 0x55FF55 : 0xFF5555, true,
                    ConfigControlView.TextAlignment.CENTER));
        }
        return new ConfigPageView(ConfigPageId.PLUGINS,
                tr("screen.mc_teamviewer.integration_plugins.title"), 24, controls);
    }

    private ConfigPageView pluginDetailPage(int width, int height) {
        PluginSnapshot plugin = selectedPluginId == null ? null : control.getIntegrationPlugin(selectedPluginId);
        int x = (width - 430) / 2;
        int y = 46;
        List<ConfigControlView> controls = new ArrayList<>();
        if (plugin == null) {
            controls.add(ConfigControlView.text(PLUGIN_PAGE_STATUS, new UiRect(x, y, 430, HEIGHT),
                    tr("screen.mc_teamviewer.integration_plugin.unavailable"), null, 0xFF5555, true,
                    ConfigControlView.TextAlignment.CENTER));
            controls.add(button(BACK, x, y + 28, 430, "screen.mc_teamviewer.config.back", null));
            return new ConfigPageView(ConfigPageId.PLUGIN_DETAIL,
                    tr("screen.mc_teamviewer.integration_plugin.title"), 24, controls);
        }
        controls.add(ConfigControlView.text(PLUGIN_PAGE_STATUS, new UiRect(x, y, 430, HEIGHT),
                t("screen.mc_teamviewer.integration_plugin.detail_header",
                        UiText.literal(plugin.id()), UiText.literal(plugin.version()),
                        runtimeStatusText(plugin.runtimeStatus())),
                pluginDiagnostic(plugin), statusColor(plugin), true,
                ConfigControlView.TextAlignment.CENTER));
        y += 22;
        for (var capability : plugin.capabilities()) {
            controls.add(ConfigControlView.text(new ConfigControlId("capability:" + capability.id()),
                    new UiRect(x, y, 430, 12),
                    t("screen.mc_teamviewer.integration_plugin.capability_entry",
                            capabilityName(capability), roleText(capability.role()),
                            supportStatusText(capability.status()), sourceText(capability.implementationSource())),
                    capabilityDiagnostic(capability),
                    capability.status() == IntegrationSupportStatus.AVAILABLE
                            ? 0x55FF55 : 0xFFAA00,
                    true, ConfigControlView.TextAlignment.LEFT));
            y += 16;
        }
        y += 4;
        for (PluginManifest.SettingDefinition definition : plugin.settingDefinitions()) {
            ConfigControlId id = ConfigControlId.setting(plugin.id(), definition.key());
            Object value = plugin.settings().get(definition.key());
            UiText settingName = settingName(plugin, definition);
            if ("boolean".equals(definition.type())) {
                controls.add(ConfigControlView.checkbox(id, new UiRect(x, y, 430, HEIGHT),
                        settingName, null, Boolean.TRUE.equals(value)));
            } else if ("enum".equals(definition.type())) {
                controls.add(ConfigControlView.button(id, new UiRect(x, y, 430, HEIGHT),
                        t("screen.mc_teamviewer.config.value", settingName, UiText.literal(String.valueOf(value))),
                        null, true));
            } else {
                textValues.putIfAbsent(id, String.valueOf(value == null ? "" : value));
                controls.add(ConfigControlView.textField(id, new UiRect(x, y, 430, HEIGHT),
                        new UiRect(x, y - LABEL_SPACING, 430, LABEL_HEIGHT), settingName,
                        null, null, value(id), 256));
            }
            y += "boolean".equals(definition.type()) || "enum".equals(definition.type()) ? 25 : 34;
        }
        controls.add(ConfigControlView.button(ConfigControlId.plugin(plugin.id(), "toggle"),
                new UiRect(x, y, 210, HEIGHT),
                tr(plugin.enabled() ? "screen.mc_teamviewer.integration_plugin.disable"
                        : "screen.mc_teamviewer.integration_plugin.enable"), null,
                !plugin.pendingRemoval()));
        if (plugin.builtIn()) {
            controls.add(ConfigControlView.button(ConfigControlId.plugin(plugin.id(), "copy"),
                    new UiRect(x + 220, y, 210, HEIGHT),
                    tr("screen.mc_teamviewer.integration_plugin.copy_custom"), null, true));
        } else {
            controls.add(ConfigControlView.button(ConfigControlId.plugin(plugin.id(), "uninstall"),
                    new UiRect(x + 220, y, 210, HEIGHT),
                    tr(plugin.pendingRemoval()
                            ? "screen.mc_teamviewer.integration_plugin.uninstall_pending"
                            : "screen.mc_teamviewer.integration_plugin.uninstall"),
                    tr("screen.mc_teamviewer.integration_plugin.uninstall.tooltip"),
                    !plugin.pendingRemoval()));
        }
        y += 26;
        controls.add(ConfigControlView.text(PLUGIN_OPERATION_STATUS, new UiRect(x, y, 430, HEIGHT),
                lastPluginOperation == null ? UiText.literal("") : operationResultText(lastPluginOperation),
                lastPluginOperation == null ? null : operationDetail(lastPluginOperation),
                lastPluginOperation != null && lastPluginOperation.succeeded() ? 0x55FF55 : 0xFF5555,
                lastPluginOperation != null, ConfigControlView.TextAlignment.CENTER));
        y += lastPluginOperation == null ? 0 : 24;
        controls.add(button(BACK, x, y, 430, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.PLUGIN_DETAIL, pluginName(plugin), 24, controls);
    }

    private ConfigPageView pluginCopyGuidePage(int width, int height) {
        int x = (width - 520) / 2;
        int y = 48;
        List<ConfigControlView> controls = new ArrayList<>();
        controls.add(infoLine(x, y, 520, "screen.mc_teamviewer.integration_plugin.copy_success", 0x55FF55));
        y += 22;
        controls.add(ConfigControlView.text(PLUGIN_PAGE_STATUS, new UiRect(x, y, 520, 24),
                t("screen.mc_teamviewer.integration_plugin.copy_path",
                        UiText.literal(copiedPluginPath == null ? "-" : copiedPluginPath.toAbsolutePath().toString())),
                copiedPluginPath == null ? null : UiText.literal(copiedPluginPath.toAbsolutePath().toString()),
                0xFFFFFF, true, ConfigControlView.TextAlignment.CENTER));
        y += 30;
        for (String key : List.of(
                "screen.mc_teamviewer.integration_plugin.copy_step_manifest",
                "screen.mc_teamviewer.integration_plugin.copy_step_lua",
                "screen.mc_teamviewer.integration_plugin.copy_step_provides",
                "screen.mc_teamviewer.integration_plugin.copy_step_restart",
                "screen.mc_teamviewer.integration_plugin.copy_step_readme")) {
            controls.add(infoLine(x, y, 520, key, 0xDDDDDD));
            y += 20;
        }
        controls.add(ConfigControlView.text(PLUGIN_OPERATION_STATUS, new UiRect(x, y, 520, HEIGHT),
                tr("screen.mc_teamviewer.integration_plugin.open_directory_failed"), null,
                0xFF5555, openDirectoryFailed, ConfigControlView.TextAlignment.CENTER));
        y += openDirectoryFailed ? 22 : 0;
        controls.add(ConfigControlView.button(PLUGIN_GUIDE_OPEN_DIRECTORY, new UiRect(x, y, 250, HEIGHT),
                tr("screen.mc_teamviewer.integration_plugin.open_directory"), null, copiedPluginPath != null));
        controls.add(ConfigControlView.button(PLUGIN_GUIDE_RETURN_LIST, new UiRect(x + 270, y, 250, HEIGHT),
                tr("screen.mc_teamviewer.integration_plugin.return_manager"), null, true));
        return new ConfigPageView(ConfigPageId.PLUGIN_COPY_GUIDE,
                tr("screen.mc_teamviewer.integration_plugin.copy_guide_title"), 24, controls);
    }

    private ConfigPageView disabledPluginsPage(int width, int height) {
        List<DisabledPluginSnapshot> plugins = control.getDisabledIntegrationPlugins();
        int pageSize = Math.max(4, Math.min(9, (height - 150) / 24));
        int pageCount = Math.max(1, (plugins.size() + pageSize - 1) / pageSize);
        disabledPluginListPage = Math.min(disabledPluginListPage, pageCount - 1);
        int start = disabledPluginListPage * pageSize;
        int end = Math.min(plugins.size(), start + pageSize);
        int x = (width - 430) / 2;
        int y = 52;
        List<ConfigControlView> controls = new ArrayList<>();
        for (int index = start; index < end; index++) {
            DisabledPluginSnapshot plugin = plugins.get(index);
            controls.add(ConfigControlView.button(ConfigControlId.plugin(plugin.storageId(), "disabled-open"),
                    new UiRect(x, y, 430, HEIGHT),
                    t("screen.mc_teamviewer.integration_plugin.disabled_entry",
                            UiText.literal(plugin.name()), UiText.literal(plugin.version())), null, true));
            y += 24;
        }
        if (plugins.isEmpty()) {
            controls.add(ConfigControlView.text(DISABLED_PLUGIN_EMPTY_STATUS, new UiRect(x, y, 430, HEIGHT),
                    tr("screen.mc_teamviewer.integration_plugin.disabled_none"), null, 0xAAAAAA, true,
                    ConfigControlView.TextAlignment.CENTER));
            y += 24;
        }
        controls.add(ConfigControlView.button(DISABLED_PLUGIN_PREVIOUS, new UiRect(x, y, 90, HEIGHT),
                UiText.literal("<"), null, disabledPluginListPage > 0));
        controls.add(ConfigControlView.text(PLUGIN_PAGE_STATUS, new UiRect(x + 94, y, 242, HEIGHT),
                UiText.literal((disabledPluginListPage + 1) + " / " + pageCount), null,
                0xFFFFFF, true, ConfigControlView.TextAlignment.CENTER));
        controls.add(ConfigControlView.button(DISABLED_PLUGIN_NEXT, new UiRect(x + 340, y, 90, HEIGHT),
                UiText.literal(">"), null, disabledPluginListPage + 1 < pageCount));
        y += 26;
        if (lastPluginOperation != null) {
            controls.add(ConfigControlView.text(PLUGIN_OPERATION_STATUS, new UiRect(x, y, 430, HEIGHT),
                    operationResultText(lastPluginOperation), operationDetail(lastPluginOperation),
                    lastPluginOperation.succeeded() ? 0x55FF55 : 0xFF5555, true,
                    ConfigControlView.TextAlignment.CENTER));
            y += 24;
        }
        controls.add(button(BACK, x, y, 430, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.DISABLED_PLUGINS,
                tr("screen.mc_teamviewer.integration_plugin.disabled_title"), 24, controls);
    }

    private ConfigPageView disabledPluginDetailPage(int width, int height) {
        DisabledPluginSnapshot plugin = selectedDisabledPluginId == null
                ? null : control.getDisabledIntegrationPlugin(selectedDisabledPluginId);
        int x = (width - 460) / 2;
        int y = 54;
        List<ConfigControlView> controls = new ArrayList<>();
        if (plugin == null) {
            controls.add(infoLine(x, y, 460,
                    "screen.mc_teamviewer.integration_plugin.disabled_unavailable", 0xFF5555));
            controls.add(button(BACK, x, y + 28, 460, "screen.mc_teamviewer.config.back", null));
            return new ConfigPageView(ConfigPageId.DISABLED_PLUGIN_DETAIL,
                    tr("screen.mc_teamviewer.integration_plugin.disabled_detail_title"), 24, controls);
        }
        controls.add(ConfigControlView.text(PLUGIN_PAGE_STATUS, new UiRect(x, y, 460, HEIGHT),
                t("screen.mc_teamviewer.integration_plugin.disabled_header",
                        UiText.literal(plugin.name()), UiText.literal(plugin.version())), null,
                0xFFAA00, true, ConfigControlView.TextAlignment.CENTER));
        y += 24;
        controls.add(ConfigControlView.text(DISABLED_PLUGIN_PATH, new UiRect(x, y, 460, HEIGHT),
                t("screen.mc_teamviewer.integration_plugin.disabled_path",
                        UiText.literal(plugin.storagePath().toAbsolutePath().toString())),
                UiText.literal(plugin.storagePath().toAbsolutePath().toString()), 0xDDDDDD, true,
                ConfigControlView.TextAlignment.CENTER));
        y += 30;
        controls.add(ConfigControlView.button(ConfigControlId.plugin(plugin.storageId(), "disabled-restore"),
                new UiRect(x, y, 145, HEIGHT), tr("screen.mc_teamviewer.integration_plugin.restore"), null, true));
        controls.add(ConfigControlView.button(ConfigControlId.plugin(plugin.storageId(), "disabled-open-dir"),
                new UiRect(x + 157, y, 145, HEIGHT), tr("screen.mc_teamviewer.integration_plugin.open_directory"), null, true));
        controls.add(ConfigControlView.button(ConfigControlId.plugin(plugin.storageId(), "disabled-delete"),
                new UiRect(x + 314, y, 146, HEIGHT), tr("screen.mc_teamviewer.integration_plugin.delete"),
                tr("screen.mc_teamviewer.integration_plugin.delete.tooltip"), true));
        y += 28;
        controls.add(ConfigControlView.text(PLUGIN_OPERATION_STATUS, new UiRect(x, y, 460, HEIGHT),
                lastPluginOperation == null ? UiText.literal("") : operationResultText(lastPluginOperation),
                lastPluginOperation == null ? null : operationDetail(lastPluginOperation),
                lastPluginOperation != null && lastPluginOperation.succeeded() ? 0x55FF55 : 0xFF5555,
                lastPluginOperation != null, ConfigControlView.TextAlignment.CENTER));
        y += lastPluginOperation == null ? 0 : 24;
        controls.add(button(BACK, x, y, 460, "screen.mc_teamviewer.config.back", null));
        return new ConfigPageView(ConfigPageId.DISABLED_PLUGIN_DETAIL,
                tr("screen.mc_teamviewer.integration_plugin.disabled_detail_title"), 24, controls);
    }

    private ConfigPageView pluginDeleteConfirmPage(int width, int height) {
        DisabledPluginSnapshot plugin = pendingDeleteDisabledPluginId == null
                ? null : control.getDisabledIntegrationPlugin(pendingDeleteDisabledPluginId);
        int x = (width - 430) / 2;
        int y = height / 2 - 55;
        List<ConfigControlView> controls = new ArrayList<>();
        controls.add(ConfigControlView.text(PLUGIN_PAGE_STATUS, new UiRect(x, y, 430, 40),
                plugin == null ? tr("screen.mc_teamviewer.integration_plugin.disabled_unavailable")
                        : t("screen.mc_teamviewer.integration_plugin.delete_confirm_message",
                        UiText.literal(plugin.name())), null, 0xFF5555, true,
                ConfigControlView.TextAlignment.CENTER));
        y += 48;
        if (plugin != null) {
            controls.add(ConfigControlView.button(
                    ConfigControlId.plugin(plugin.storageId(), "disabled-delete-confirm"),
                    new UiRect(x, y, 210, HEIGHT),
                    tr("screen.mc_teamviewer.integration_plugin.delete_confirm"), null, true));
        }
        controls.add(button(BACK, x + 220, y, 210, "screen.mc_teamviewer.config.cancel", null));
        return new ConfigPageView(ConfigPageId.PLUGIN_DELETE_CONFIRM,
                tr("screen.mc_teamviewer.integration_plugin.delete_confirm_title"), 24, controls);
    }

    private void applyPageFields(ConfigPageId page) {
        switch (page) {
            case DISPLAY -> applyDisplayFields();
            case NETWORK -> applyNetworkFields();
            case COLOR -> applyColorFields();
            case WAYPOINT -> applyWaypointFields();
            case WAYPOINT_SHAPE -> applyWaypointShapeFields();
            case PLUGIN_DETAIL -> applyPluginFields();
            default -> { }
        }
    }

    private void applyPluginFields() {
        PluginSnapshot plugin = selectedPluginId == null ? null : control.getIntegrationPlugin(selectedPluginId);
        if (plugin == null) return;
        for (PluginManifest.SettingDefinition definition : plugin.settingDefinitions()) {
            if ("boolean".equals(definition.type()) || "enum".equals(definition.type())) continue;
            ConfigControlId id = ConfigControlId.setting(plugin.id(), definition.key());
            control.setIntegrationPluginSetting(plugin.id(), definition.key(), value(id));
        }
    }

    private ConfigUiAction activatePluginAction(ConfigControlId id) {
        String action = id.pluginAction();
        if ("disabled-open".equals(action)) {
            selectedDisabledPluginId = id.pluginId();
            lastPluginOperation = null;
            return ConfigUiAction.open(ConfigPageId.DISABLED_PLUGIN_DETAIL);
        }
        if ("disabled-restore".equals(action)) {
            lastPluginOperation = control.restoreIntegrationPlugin(id.pluginId());
            return lastPluginOperation.succeeded()
                    ? ConfigUiAction.open(ConfigPageId.DISABLED_PLUGINS) : ConfigUiAction.stay();
        }
        if ("disabled-open-dir".equals(action)) {
            DisabledPluginSnapshot disabled = control.getDisabledIntegrationPlugin(id.pluginId());
            boolean opened = disabled != null && control.openIntegrationPluginDirectory(disabled.storagePath());
            if (!opened) {
                lastPluginOperation = new PluginFileOperationResult(
                        PluginFileOperationResult.Code.IO_ERROR,
                        disabled == null ? null : disabled.storagePath(), "Unable to open directory");
            }
            return ConfigUiAction.stay();
        }
        if ("disabled-delete".equals(action)) {
            pendingDeleteDisabledPluginId = id.pluginId();
            return ConfigUiAction.open(ConfigPageId.PLUGIN_DELETE_CONFIRM);
        }
        if ("disabled-delete-confirm".equals(action)) {
            lastPluginOperation = control.deleteDisabledIntegrationPlugin(id.pluginId());
            pendingDeleteDisabledPluginId = null;
            selectedDisabledPluginId = null;
            return ConfigUiAction.open(ConfigPageId.DISABLED_PLUGINS);
        }

        PluginSnapshot plugin = control.getIntegrationPlugin(id.pluginId());
        if ("open".equals(action)) {
            selectedPluginId = id.pluginId();
            lastPluginOperation = null;
            if (plugin != null) {
                plugin.settings().forEach((key, value) -> textValues.put(
                        ConfigControlId.setting(plugin.id(), key), String.valueOf(value)));
            }
            return ConfigUiAction.open(ConfigPageId.PLUGIN_DETAIL);
        }
        if (plugin == null) return ConfigUiAction.stay();
        if ("toggle".equals(action)) {
            control.setIntegrationPluginEnabled(plugin.id(), !plugin.enabled());
        } else if ("copy".equals(action)) {
            lastPluginOperation = control.copyBuiltinIntegrationPluginResult(plugin.id());
            if (lastPluginOperation.succeeded()) {
                copiedPluginPath = lastPluginOperation.path();
                openDirectoryFailed = false;
                return ConfigUiAction.open(ConfigPageId.PLUGIN_COPY_GUIDE);
            }
        } else if ("uninstall".equals(action)) {
            lastPluginOperation = control.uninstallIntegrationPlugin(plugin.id());
            if (lastPluginOperation.succeeded()) return ConfigUiAction.open(ConfigPageId.PLUGINS);
        }
        return ConfigUiAction.stay();
    }

    private ConfigUiAction activatePluginSetting(ConfigControlId id) {
        PluginSnapshot plugin = control.getIntegrationPlugin(id.pluginId());
        if (plugin == null) return ConfigUiAction.stay();
        PluginManifest.SettingDefinition definition = plugin.settingDefinitions().stream()
                .filter(value -> value.key().equals(id.settingKey())).findFirst().orElse(null);
        if (definition == null || !"enum".equals(definition.type())) return ConfigUiAction.stay();
        String current = String.valueOf(plugin.settings().get(definition.key()));
        int index = definition.options().indexOf(current);
        String next = definition.options().get((index + 1 + definition.options().size()) % definition.options().size());
        control.setIntegrationPluginSetting(plugin.id(), definition.key(), next);
        return ConfigUiAction.stay();
    }

    private static int statusColor(PluginSnapshot plugin) {
        return switch (plugin.runtimeStatus()) {
            case ACTIVE -> 0x55FF55;
            case DISABLED -> 0xAAAAAA;
            case PENDING_RESTART -> 0xFFFF55;
            default -> 0xFF5555;
        };
    }

    private void applyDisplayFields() {
        try {
            String distance = value(RENDER_DISTANCE).trim();
            if (!distance.isEmpty()) {
                int parsed = Integer.parseInt(distance);
                if (parsed > 0) config.setRenderDistance(parsed);
            }
            String offset = value(TRACER_TOP_OFFSET).trim();
            if (!offset.isEmpty()) config.setTracerTopOffset(Double.parseDouble(offset));
        } catch (NumberFormatException ignored) { }
    }

    private void applyNetworkFields() {
        try {
            int update = Integer.parseInt(value(UPDATE_INTERVAL).trim());
            if (update > 0) config.setUpdateInterval(update);
            int battleUpdate = Integer.parseInt(value(BATTLE_MAP_UPDATE_INTERVAL).trim());
            if (battleUpdate > 0) config.setBattleMapUpdateIntervalTicks(battleUpdate);
            int keepalive = Integer.parseInt(value(BATTLE_MAP_KEEPALIVE_INTERVAL).trim());
            if (keepalive > 0) config.setBattleMapKeepaliveIntervalSeconds(keepalive);
            int retention = Integer.parseInt(value(BATTLE_MAP_CACHE_RETENTION).trim());
            if (retention > 0) config.setBattleMapCacheRetentionSeconds(retention);
        } catch (NumberFormatException ignored) { }
    }

    private void applyColorFields() {
        try {
            Integer parsed = parseColor(value(BOX_COLOR));
            if (parsed != null) config.setBoxColor(parsed);
            parsed = parseColor(value(LINE_COLOR));
            if (parsed != null) config.setLineColor(parsed);
            parsed = parseColor(value(FRIENDLY_TEAM_COLOR));
            if (parsed != null) config.setFriendlyTeamColor(parsed);
            parsed = parseColor(value(NEUTRAL_TEAM_COLOR));
            if (parsed != null) config.setNeutralTeamColor(parsed);
            parsed = parseColor(value(ENEMY_TEAM_COLOR));
            if (parsed != null) config.setEnemyTeamColor(parsed);
        } catch (NumberFormatException ignored) { }
    }

    private void applyWaypointFields() {
        try {
            String normal = value(WAYPOINT_TIMEOUT).trim();
            if (!normal.isEmpty()) config.setWaypointTimeoutSeconds(Integer.parseInt(normal));
            String longTerm = value(LONG_TERM_WAYPOINT_TIMEOUT).trim();
            if (!longTerm.isEmpty()) config.setLongTermWaypointTimeoutSeconds(Integer.parseInt(longTerm));
            String max = value(QUICK_MARK_MAX_COUNT).trim();
            if (!max.isEmpty()) config.setMaxQuickMarkCount(Integer.parseInt(max));
        } catch (NumberFormatException ignored) { }
    }

    private void applyWaypointShapeFields() {
        try {
            config.setWaypointBeaconBeamWidth(Double.parseDouble(value(WAYPOINT_BEAM_WIDTH).trim()));
            config.setWaypointBeaconBeamHeight(Double.parseDouble(value(WAYPOINT_BEAM_HEIGHT).trim()));
            config.setTampermonkeyBeamWidth(Double.parseDouble(value(TAMPERMONKEY_BEAM_WIDTH).trim()));
            config.setTampermonkeyBeamHeight(Double.parseDouble(value(TAMPERMONKEY_BEAM_HEIGHT).trim()));
        } catch (NumberFormatException ignored) { }
    }

    private ConfigUiAction toggleAutoConnect() {
        config.setAutoConnectOnMultiplayerJoin(!config.isAutoConnectOnMultiplayerJoin());
        return ConfigUiAction.stay();
    }

    private ConfigUiAction connect() {
        saveRoot();
        if (control.isEnabled()) {
            control.reconnect();
        } else {
            control.setEnabled(true);
            control.reconnect();
        }
        return ConfigUiAction.stay();
    }

    private ConfigUiAction disconnect() {
        control.disconnect();
        return ConfigUiAction.stay();
    }

    private ConfigUiAction cycleTracerMode() {
        config.setTracerStartMode(config.isTracerStartTop() ? Config.TRACER_START_CROSSHAIR : Config.TRACER_START_TOP);
        return ConfigUiAction.stay();
    }

    private ConfigUiAction cycleWaypointStyle() {
        String next = switch (config.getWaypointUiStyle()) {
            case Config.WAYPOINT_UI_BEACON -> Config.WAYPOINT_UI_RING;
            case Config.WAYPOINT_UI_RING -> Config.WAYPOINT_UI_PIN;
            default -> Config.WAYPOINT_UI_BEACON;
        };
        config.setWaypointUiStyle(next);
        return ConfigUiAction.stay();
    }

    private ConfigUiAction cycleBattleMapMode() {
        List<String> sourceIds = control.getIntegrationCapabilities().stream()
                .filter(capability -> IntegrationRole.BATTLE_MAP_SOURCE.id().equals(capability.role()))
                .map(IntegrationCapability::id)
                .distinct().toList();
        if (sourceIds.isEmpty()) return ConfigUiAction.stay();
        String current = config.getBattleMapSourceId();
        int index = sourceIds.indexOf(current);
        config.setBattleMapSourceId(sourceIds.get((index + 1 + sourceIds.size()) % sourceIds.size()));
        return ConfigUiAction.stay();
    }

    private UiText battleMapSourceLabel() {
        String sourceId = config.getBattleMapSourceId();
        IntegrationCapability capability = battleMapCapability(sourceId);
        UiText name = capability == null ? UiText.literal(sourceId) : capabilityName(capability);
        if (capability == null) return name;
        return capability.status() == IntegrationSupportStatus.AVAILABLE
                ? name
                : t("screen.mc_teamviewer.integration_plugin.capability_with_status",
                name, supportStatusText(capability.status()));
    }

    private UiText battleMapSourceTooltip() {
        IntegrationCapability capability = battleMapCapability(config.getBattleMapSourceId());
        if (capability == null) return null;
        return capabilityDiagnostic(capability);
    }

    private IntegrationCapability battleMapCapability(String sourceId) {
        return control.getIntegrationCapabilities().stream()
                .filter(capability -> capability.id().equals(sourceId))
                .findFirst().orElse(null);
    }

    private ConfigUiAction startPacketCapture() {
        control.getNetworkManager().startPacketDumpCapture();
        control.showActionBar("§c[TV] 已开始抓包，游戏内将持续显示抓包提示");
        return ConfigUiAction.stay();
    }

    private ConfigUiAction stopPacketCapture() {
        control.getNetworkManager().stopPacketDumpCapture();
        control.showActionBar("§a[TV] 已结束抓包");
        return ConfigUiAction.stay();
    }

    private ConfigUiAction toggle(boolean current, BooleanSetter setter) {
        setter.set(!current);
        return ConfigUiAction.stay();
    }

    private ConfigControlView field(ConfigControlId id, int x, int y, int width, String label,
                                    String hint, String tooltip, int maxLength) {
        return ConfigControlView.textField(id, new UiRect(x, y, width, HEIGHT),
                new UiRect(x, y - LABEL_SPACING, width, LABEL_HEIGHT), tr(label),
                hint == null ? null : tr(hint), tooltip == null ? null : tr(tooltip), value(id), maxLength);
    }

    private ConfigControlView button(ConfigControlId id, int x, int y, int width, String label, String tooltip) {
        return ConfigControlView.button(id, new UiRect(x, y, width, HEIGHT), tr(label),
                tooltip == null ? null : tr(tooltip), true);
    }

    private ConfigControlView toggleButton(ConfigControlId id, int x, int y, int width, String label, boolean enabled) {
        return ConfigControlView.button(id, new UiRect(x, y, width, HEIGHT), UiText.toggle(label, enabled),
                null, true);
    }

    private ConfigControlView toggleButtonWithTooltip(ConfigControlId id, int x, int y, int width, String label, boolean enabled) {
        return ConfigControlView.button(id, new UiRect(x, y, width, HEIGHT), UiText.toggle(label, enabled),
                tr(label + ".tooltip"), true);
    }

    private static ConfigControlView infoLine(int x, int y, int width, String key, int color) {
        return ConfigControlView.text(new ConfigControlId("info:" + key), new UiRect(x, y, width, HEIGHT),
                tr(key), null, color, true, ConfigControlView.TextAlignment.CENTER);
    }

    private static UiText pluginName(PluginSnapshot plugin) {
        return switch (plugin.id()) {
            case IntegrationIds.PLUGIN_NODEMC -> tr("screen.mc_teamviewer.integration_plugin.builtin.nodemc");
            case IntegrationIds.PLUGIN_SIMMC -> tr("screen.mc_teamviewer.integration_plugin.builtin.simmc");
            case IntegrationIds.PLUGIN_XAERO -> tr("screen.mc_teamviewer.integration_plugin.builtin.xaero");
            case IntegrationIds.PLUGIN_JOURNEYMAP -> tr("screen.mc_teamviewer.integration_plugin.builtin.journeymap");
            case IntegrationIds.PLUGIN_EXAMPLE -> tr("screen.mc_teamviewer.integration_plugin.builtin.example");
            default -> UiText.literal(plugin.name());
        };
    }

    private static UiText capabilityName(IntegrationCapability capability) {
        return switch (capability.id()) {
            case IntegrationIds.JOURNEYMAP_PLAYERS -> tr("screen.mc_teamviewer.integration_plugin.capability.journeymap_players");
            case IntegrationIds.JOURNEYMAP_BEACONS -> tr("screen.mc_teamviewer.integration_plugin.capability.journeymap_beacons");
            case IntegrationIds.JOURNEYMAP_WAYPOINTS -> tr("screen.mc_teamviewer.integration_plugin.capability.journeymap_waypoints");
            case IntegrationIds.XAERO_WORLDMAP -> tr("screen.mc_teamviewer.integration_plugin.capability.xaero_worldmap");
            case IntegrationIds.XAERO_MINIMAP -> tr("screen.mc_teamviewer.integration_plugin.capability.xaero_minimap");
            case IntegrationIds.NODEMC_BATTLE_MAP -> tr("screen.mc_teamviewer.integration_plugin.capability.nodemc_battle_map");
            case IntegrationIds.SIMMC_BATTLE_MAP -> tr("screen.mc_teamviewer.integration_plugin.capability.simmc_battle_map");
            case IntegrationIds.EXAMPLE_REMOTE_PLAYER -> tr("screen.mc_teamviewer.integration_plugin.capability.example_remote_player");
            case IntegrationIds.EXAMPLE_SHARED_WAYPOINT -> tr("screen.mc_teamviewer.integration_plugin.capability.example_shared_waypoint");
            case IntegrationIds.EXAMPLE_BATTLE_MAP -> tr("screen.mc_teamviewer.integration_plugin.capability.example_battle_map");
            default -> UiText.literal(capability.displayName());
        };
    }

    private static UiText settingName(PluginSnapshot plugin, PluginManifest.SettingDefinition setting) {
        if (IntegrationIds.PLUGIN_JOURNEYMAP.equals(plugin.id())) {
            if ("show_beacons".equals(setting.key())) {
                return tr("screen.mc_teamviewer.integration_plugin.setting.journeymap_show_beacons");
            }
            if ("show_map_markers".equals(setting.key())) {
                return tr("screen.mc_teamviewer.integration_plugin.setting.journeymap_show_markers");
            }
        }
        if (IntegrationIds.PLUGIN_EXAMPLE.equals(plugin.id())) {
            return tr("screen.mc_teamviewer.integration_plugin.setting.example_" + setting.key());
        }
        return UiText.literal(setting.name());
    }

    private static UiText runtimeStatusText(PluginRuntimeStatus status) {
        return tr("screen.mc_teamviewer.integration_plugin.runtime." + status.name().toLowerCase(Locale.ROOT));
    }

    private static UiText supportStatusText(IntegrationSupportStatus status) {
        return tr("screen.mc_teamviewer.integration_plugin.support." + status.name().toLowerCase(Locale.ROOT));
    }

    private static UiText sourceText(IntegrationImplementationSource source) {
        return tr("screen.mc_teamviewer.integration_plugin.source." + source.name().toLowerCase(Locale.ROOT));
    }

    private static UiText roleText(String role) {
        return tr("screen.mc_teamviewer.integration_plugin.role." + role.replace('-', '_'));
    }

    private static UiText pluginDiagnostic(PluginSnapshot plugin) {
        if (plugin.detail().isBlank()) return null;
        return t("screen.mc_teamviewer.integration_plugin.technical_detail",
                runtimeStatusText(plugin.runtimeStatus()), UiText.literal(plugin.detail()));
    }

    private static UiText capabilityDiagnostic(IntegrationCapability capability) {
        if (capability.detail().isBlank()) return null;
        return t("screen.mc_teamviewer.integration_plugin.technical_detail",
                supportStatusText(capability.status()), UiText.literal(capability.detail()));
    }

    private static UiText operationResultText(PluginFileOperationResult result) {
        String suffix = switch (result.code()) {
            case SUCCESS -> "success";
            case NOT_FOUND -> "not_found";
            case BUILTIN_READ_ONLY -> "builtin_read_only";
            case INVALID_SOURCE -> "invalid_source";
            case TARGET_EXISTS -> "target_exists";
            case INVALID_DISABLED_ENTRY -> "invalid_disabled_entry";
            case IO_ERROR -> "io_error";
        };
        return tr("screen.mc_teamviewer.integration_plugin.operation." + suffix);
    }

    private static UiText operationDetail(PluginFileOperationResult result) {
        if (result.detail().isBlank() && result.path() == null) return null;
        return t("screen.mc_teamviewer.integration_plugin.operation_detail",
                UiText.literal(result.path() == null ? "-" : result.path().toAbsolutePath().toString()),
                UiText.literal(result.detail().isBlank() ? "-" : result.detail()));
    }

    private String value(ConfigControlId id) {
        return textValues.getOrDefault(id, "");
    }

    private static UiText tr(String key) {
        return UiText.translatable(key);
    }

    private static UiText t(String key, UiText... arguments) {
        return UiText.translatable(key, arguments);
    }

    private static String color(int value) {
        return String.format(Locale.ROOT, "0x%08X", value);
    }

    private static Integer parseColor(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String text = raw.trim();
        if (text.startsWith("0x") || text.startsWith("0X")) return (int) Long.parseLong(text.substring(2), 16);
        if (text.startsWith("#")) {
            String hex = text.substring(1);
            if (hex.length() == 6) return 0xFF000000 | Integer.parseInt(hex, 16);
            if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
        }
        return Integer.parseInt(text);
    }

    private static String statusKey(NetworkManager.ConnectionStage stage) {
        return switch (stage) {
            case CONNECTED -> "connection.status.connected";
            case CONNECTING -> "connection.status.connecting";
            case WS_CONNECTED_HANDSHAKING -> "connection.status.ws_connected_handshaking";
            case FAILED -> "connection.status.failed";
            case DISCONNECTED -> "connection.status.disconnected";
        };
    }

    private static int statusColor(NetworkManager.ConnectionStage stage) {
        return switch (stage) {
            case CONNECTED -> 0x00FF00;
            case CONNECTING -> 0xFFFF55;
            case WS_CONNECTED_HANDSHAKING -> 0x55FFFF;
            case FAILED -> 0xFFAA00;
            case DISCONNECTED -> 0xFF0000;
        };
    }

    private static String abbreviatePath(String path) {
        return path == null || path.length() <= 36 ? fallback(path, "") : "..." + path.substring(path.length() - 33);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String fallback(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }
}
