package fun.prof_chen.teamviewer.main_code.config.ui;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.abstraction.TransportProcess;
import fun.prof_chen.teamviewer.main_code.plugin.PluginManifest;
import fun.prof_chen.teamviewer.main_code.plugin.PluginSnapshot;
import fun.prof_chen.teamviewer.main_code.plugin.DisabledPluginSnapshot;
import fun.prof_chen.teamviewer.main_code.plugin.PluginFileOperationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUiSessionTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesAllSevenCompletePages() {
        ConfigUiSession session = session(new Config());
        assertEquals(EnumSet.allOf(ConfigPageId.class), EnumSet.allOf(ConfigPageId.class).stream()
                .filter(page -> !session.page(page, 854, 480).controls().isEmpty())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ConfigPageId.class))));
        for (ConfigPageId page : ConfigPageId.values()) {
            List<ConfigControlId> ids = session.page(page, 854, 480).controls().stream()
                    .map(ConfigControlView::id).toList();
            assertEquals(ids.size(), Set.copyOf(ids).size(), page + " contains duplicate control IDs");
        }

        assertContains(session, ConfigPageId.ROOT,
                ConfigControlId.SERVER_URL, ConfigControlId.ROOM_CODE, ConfigControlId.ALLOW_INSECURE_TLS,
                ConfigControlId.OPEN_DISPLAY, ConfigControlId.OPEN_NETWORK, ConfigControlId.CONNECT,
                ConfigControlId.DISCONNECT, ConfigControlId.CONNECTION_STATUS);
        assertContains(session, ConfigPageId.DISPLAY,
                ConfigControlId.RENDER_DISTANCE, ConfigControlId.SHOW_BOXES, ConfigControlId.SHOW_LINES,
                ConfigControlId.TRACER_START_MODE, ConfigControlId.OPEN_COLOR, ConfigControlId.OPEN_WAYPOINT);
        assertTrue(session.page(ConfigPageId.DISPLAY, 854, 480).controls().stream()
                .noneMatch(control -> control.id().value().startsWith("JOURNEYMAP_REMOTE_")),
                "JourneyMap-owned settings must only be rendered by the plugin detail page");
        assertContains(session, ConfigPageId.NETWORK,
                ConfigControlId.UPDATE_INTERVAL, ConfigControlId.UPLOAD_ENTITIES,
                ConfigControlId.BATTLE_MAP_SOURCE, ConfigControlId.OPEN_PACKET_CAPTURE);
        assertContains(session, ConfigPageId.WAYPOINT,
                ConfigControlId.WAYPOINT_TIMEOUT, ConfigControlId.QUICK_MARK_MAX_COUNT,
                ConfigControlId.MIDDLE_DOUBLE_CLICK_MARK, ConfigControlId.AUTO_CANCEL_ON_ENTITY_DEATH,
                ConfigControlId.OPEN_WAYPOINT_SHAPE);
        assertContains(session, ConfigPageId.COLOR,
                ConfigControlId.BOX_COLOR, ConfigControlId.LINE_COLOR, ConfigControlId.FRIENDLY_TEAM_COLOR,
                ConfigControlId.NEUTRAL_TEAM_COLOR, ConfigControlId.ENEMY_TEAM_COLOR);
        assertContains(session, ConfigPageId.WAYPOINT_SHAPE,
                ConfigControlId.WAYPOINT_BEAM_WIDTH, ConfigControlId.WAYPOINT_BEAM_HEIGHT,
                ConfigControlId.TAMPERMONKEY_BEAM_WIDTH, ConfigControlId.TAMPERMONKEY_BEAM_HEIGHT);
        assertContains(session, ConfigPageId.PACKET_CAPTURE,
                ConfigControlId.PACKET_CAPTURE_START, ConfigControlId.PACKET_CAPTURE_STOP,
                ConfigControlId.PACKET_CAPTURE_CURRENT_PATH, ConfigControlId.PACKET_CAPTURE_LAST_PATH);
    }

    @Test
    void rootCancelAndSaveMatchLegacySemantics() {
        Config config = new Config();
        config.setStoragePath(tempDir.resolve("teamviewer.json"));
        config.setServerURL("ws://old");
        config.setRoomCode("old-room");
        ConfigUiSession session = session(config);

        session.setText(ConfigControlId.SERVER_URL, "ws://new");
        session.setText(ConfigControlId.ROOM_CODE, "new-room");
        session.activate(ConfigPageId.ROOT, ConfigControlId.AUTO_CONNECT);
        session.setChecked(ConfigControlId.ALLOW_INSECURE_TLS, true);
        assertTrue(session.hasUnsavedRootChanges());

        session.cancelRoot();
        assertEquals("ws://old", config.getServerURL());
        assertEquals("old-room", config.getRoomCode());
        assertFalse(config.isAutoConnectOnMultiplayerJoin());
        assertFalse(config.isAllowInsecureTls());

        session.setText(ConfigControlId.SERVER_URL, "ws://saved");
        session.setText(ConfigControlId.ROOM_CODE, "saved-room");
        session.setChecked(ConfigControlId.ALLOW_INSECURE_TLS, true);
        session.saveRoot();
        assertEquals("ws://saved", config.getServerURL());
        assertEquals("saved-room", config.getRoomCode());
        assertTrue(config.isAllowInsecureTls());
        assertFalse(session.hasUnsavedRootChanges());
        assertTrue(tempDir.resolve("teamviewer.json").toFile().isFile());
    }

    @Test
    void secondaryFieldsUseCommonValidationAndToggleLogic() {
        Config config = new Config();
        ConfigUiSession session = session(config);
        session.setText(ConfigControlId.WAYPOINT_TIMEOUT, "1");
        session.setText(ConfigControlId.LONG_TERM_WAYPOINT_TIMEOUT, "999999");
        session.setText(ConfigControlId.QUICK_MARK_MAX_COUNT, "99");
        session.close(ConfigPageId.WAYPOINT);
        assertEquals(10, config.getWaypointTimeoutSeconds());
        assertEquals(86400, config.getLongTermWaypointTimeoutSeconds());
        assertEquals(20, config.getMaxQuickMarkCount());

        boolean before = config.isShowBoxes();
        session.activate(ConfigPageId.DISPLAY, ConfigControlId.SHOW_BOXES);
        assertEquals(!before, config.isShowBoxes());
    }

    @Test
    void pluginPagesUseDynamicIdsAndForwardSettingsAndToggleActions() {
        PluginManifest.SettingDefinition setting = new PluginManifest.SettingDefinition(
                "enabled_marker", "boolean", "Enable marker", true,
                null, null, List.of(), false);
        PluginSnapshot plugin = new PluginSnapshot(
                "custom.ui", "UI plugin", "1.0.0", false, true, true,
                PluginRuntimeStatus.ACTIVE, "", tempDir.resolve("custom.ui"), Map.of("enabled_marker", true),
                List.of(setting), List.of(new IntegrationCapability(
                "custom-ui-map", "battle-map-source", IntegrationSupportStatus.AVAILABLE, "",
                "custom.ui", IntegrationImplementationSource.LUA, PluginRuntimeStatus.ACTIVE)));
        FakeControl control = new FakeControl(new Config(), plugin);
        ConfigUiSession session = new ConfigUiSession(control);
        ConfigControlId open = ConfigControlId.plugin(plugin.id(), "open");

        assertContains(session, ConfigPageId.PLUGINS, open);
        assertEquals(ConfigPageId.PLUGIN_DETAIL,
                session.activate(ConfigPageId.PLUGINS, open).targetPage());
        ConfigControlId dynamicSetting = ConfigControlId.setting(plugin.id(), "enabled_marker");
        assertContains(session, ConfigPageId.PLUGIN_DETAIL, dynamicSetting,
                ConfigControlId.plugin(plugin.id(), "toggle"));

        session.setChecked(dynamicSetting, false);
        assertEquals(false, control.changedSettings.get("enabled_marker"));
        session.activate(ConfigPageId.PLUGIN_DETAIL, ConfigControlId.plugin(plugin.id(), "toggle"));
        assertEquals(Boolean.FALSE, control.lastEnabledValue);

        session.activate(ConfigPageId.NETWORK, ConfigControlId.BATTLE_MAP_SOURCE);
        session.activate(ConfigPageId.NETWORK, ConfigControlId.BATTLE_MAP_SOURCE);
        assertEquals("custom-ui-map", control.config.getBattleMapSourceId());
    }

    @Test
    void battleMapSourceSelectorDoesNotInventUnregisteredFallbacks() {
        Config config = new Config();
        config.setBattleMapSourceId("missing-selected-source");
        ConfigUiSession session = new ConfigUiSession(new FakeControl(config));

        session.activate(ConfigPageId.NETWORK, ConfigControlId.BATTLE_MAP_SOURCE);

        assertEquals("missing-selected-source", config.getBattleMapSourceId());
    }

    @Test
    void copyActionOpensLocalizedGuideWithPathAndDirectoryAction() {
        PluginSnapshot builtIn = plugin("nodemc", true);
        FakeControl control = new FakeControl(new Config(), builtIn);
        control.copyPath = tempDir.resolve("team-view-relay/plugins/nodemc.custom");
        ConfigUiSession session = new ConfigUiSession(control);
        ConfigControlId open = ConfigControlId.plugin(builtIn.id(), "open");
        assertEquals(ConfigPageId.PLUGIN_DETAIL,
                session.activate(ConfigPageId.PLUGINS, open).targetPage());

        ConfigUiAction copied = session.activate(ConfigPageId.PLUGIN_DETAIL,
                ConfigControlId.plugin(builtIn.id(), "copy"));
        assertEquals(ConfigPageId.PLUGIN_COPY_GUIDE, copied.targetPage());
        assertContains(session, ConfigPageId.PLUGIN_COPY_GUIDE,
                ConfigControlId.PLUGIN_GUIDE_OPEN_DIRECTORY,
                ConfigControlId.PLUGIN_GUIDE_RETURN_LIST);
        ConfigPageView guide = session.page(ConfigPageId.PLUGIN_COPY_GUIDE, 854, 480);
        assertTrue(guide.controls().stream()
                .filter(value -> value.label() != null)
                .allMatch(value -> value.label().translationKey() != null),
                "framework copy-guide labels must remain translatable");

        session.activate(ConfigPageId.PLUGIN_COPY_GUIDE, ConfigControlId.PLUGIN_GUIDE_OPEN_DIRECTORY);
        assertEquals(control.copyPath, control.openedPath);
    }

    @Test
    void customAndDisabledPluginPagesExposeSafeUninstallRestoreAndDeleteFlow() {
        PluginSnapshot custom = plugin("custom.ui", false);
        FakeControl control = new FakeControl(new Config(), custom);
        DisabledPluginSnapshot disabled = new DisabledPluginSnapshot(
                "custom.ui-20260726", custom.id(), custom.name(), custom.version(),
                "custom.ui", false, 1L, tempDir.resolve("plugins-disabled/custom.ui-20260726"));
        control.disabledPlugins = List.of(disabled);
        ConfigUiSession session = new ConfigUiSession(control);

        session.activate(ConfigPageId.PLUGINS, ConfigControlId.plugin(custom.id(), "open"));
        assertContains(session, ConfigPageId.PLUGIN_DETAIL,
                ConfigControlId.plugin(custom.id(), "uninstall"));
        assertEquals(ConfigPageId.PLUGINS, session.activate(ConfigPageId.PLUGIN_DETAIL,
                ConfigControlId.plugin(custom.id(), "uninstall")).targetPage());
        assertEquals(custom.id(), control.uninstalledPluginId);

        assertContains(session, ConfigPageId.DISABLED_PLUGINS,
                ConfigControlId.plugin(disabled.storageId(), "disabled-open"));
        assertEquals(ConfigPageId.DISABLED_PLUGIN_DETAIL,
                session.activate(ConfigPageId.DISABLED_PLUGINS,
                        ConfigControlId.plugin(disabled.storageId(), "disabled-open")).targetPage());
        assertContains(session, ConfigPageId.DISABLED_PLUGIN_DETAIL,
                ConfigControlId.plugin(disabled.storageId(), "disabled-restore"),
                ConfigControlId.plugin(disabled.storageId(), "disabled-open-dir"),
                ConfigControlId.plugin(disabled.storageId(), "disabled-delete"));

        assertEquals(ConfigPageId.PLUGIN_DELETE_CONFIRM,
                session.activate(ConfigPageId.DISABLED_PLUGIN_DETAIL,
                        ConfigControlId.plugin(disabled.storageId(), "disabled-delete")).targetPage());
        assertContains(session, ConfigPageId.PLUGIN_DELETE_CONFIRM,
                ConfigControlId.plugin(disabled.storageId(), "disabled-delete-confirm"));
        assertEquals(ConfigPageId.DISABLED_PLUGINS,
                session.activate(ConfigPageId.PLUGIN_DELETE_CONFIRM,
                        ConfigControlId.plugin(disabled.storageId(), "disabled-delete-confirm")).targetPage());
        assertEquals(disabled.storageId(), control.deletedStorageId);
    }

    private PluginSnapshot plugin(String id, boolean builtIn) {
        return new PluginSnapshot(id, id + " name", "1.0.0", builtIn, true, true,
                PluginRuntimeStatus.ACTIVE, "", builtIn ? null : tempDir.resolve(id), Map.of(),
                List.of(), List.of(new IntegrationCapability(
                id + "-map", "battle-map-source", IntegrationSupportStatus.AVAILABLE, "",
                id, IntegrationImplementationSource.JAVA_NATIVE, PluginRuntimeStatus.ACTIVE)));
    }

    private ConfigUiSession session(Config config) {
        return new ConfigUiSession(new FakeControl(config));
    }

    private static void assertContains(ConfigUiSession session, ConfigPageId page, ConfigControlId... expected) {
        Set<ConfigControlId> ids = session.page(page, 854, 480).controls().stream()
                .map(ConfigControlView::id)
                .collect(Collectors.toSet());
        for (ConfigControlId id : expected) {
            assertTrue(ids.contains(id), () -> page + " missing " + id);
        }
    }

    private static final class FakeControl implements ClientControlGateway {
        private final Config config;
        private final NetworkManager network = new NetworkManager(
                new HashMap<UUID, RemotePlayerInfo>(), runtime(), (uri, options, listener) -> null);
        private final PluginSnapshot plugin;
        private final Map<String, Object> changedSettings = new HashMap<>();
        private List<DisabledPluginSnapshot> disabledPlugins = List.of();
        private Path copyPath;
        private Path openedPath;
        private String uninstalledPluginId;
        private String deletedStorageId;
        private boolean enabled;
        private Boolean lastEnabledValue;

        private FakeControl(Config config) {
            this(config, null);
        }

        private FakeControl(Config config, PluginSnapshot plugin) {
            this.config = config;
            this.plugin = plugin;
        }

        public Config getConfig() { return config; }
        public NetworkManager getNetworkManager() { return network; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void reconnect() { }
        public void showActionBar(String message) { }
        public List<PluginSnapshot> getIntegrationPlugins() { return plugin == null ? List.of() : List.of(plugin); }
        public PluginSnapshot getIntegrationPlugin(String pluginId) {
            return plugin != null && plugin.id().equals(pluginId) ? plugin : null;
        }
        public boolean setIntegrationPluginEnabled(String pluginId, boolean enabled) {
            lastEnabledValue = enabled;
            return true;
        }
        public boolean setIntegrationPluginSetting(String pluginId, String key, Object value) {
            changedSettings.put(key, value);
            return true;
        }
        public PluginFileOperationResult copyBuiltinIntegrationPluginResult(String pluginId) {
            return copyPath == null
                    ? new PluginFileOperationResult(PluginFileOperationResult.Code.IO_ERROR, null, "copy failed")
                    : PluginFileOperationResult.success(copyPath);
        }
        public List<DisabledPluginSnapshot> getDisabledIntegrationPlugins() { return disabledPlugins; }
        public DisabledPluginSnapshot getDisabledIntegrationPlugin(String storageId) {
            return disabledPlugins.stream().filter(value -> value.storageId().equals(storageId))
                    .findFirst().orElse(null);
        }
        public PluginFileOperationResult uninstallIntegrationPlugin(String pluginId) {
            uninstalledPluginId = pluginId;
            return PluginFileOperationResult.success(tempDirPath(pluginId));
        }
        public PluginFileOperationResult restoreIntegrationPlugin(String storageId) {
            return PluginFileOperationResult.success(tempDirPath(storageId));
        }
        public PluginFileOperationResult deleteDisabledIntegrationPlugin(String storageId) {
            deletedStorageId = storageId;
            return PluginFileOperationResult.success(tempDirPath(storageId));
        }
        public boolean openIntegrationPluginDirectory(Path path) {
            openedPath = path;
            return true;
        }

        private static Path tempDirPath(String value) {
            return Path.of("build", "plugin-test", value);
        }

        private static RuntimeGateway runtime() {
            return new RuntimeGateway() {
                public String getCurrentDimensionId() { return "minecraft:overworld"; }
                public UUID getLocalPlayerId() { return null; }
                public String getClientProgramVersion() { return "test"; }
                public String getClientProtocolVersion() { return "0.6.2"; }
                public String getClientMinCompatibleProtocolVersion() { return "0.6.1"; }
                public String getServerProtocolFallbackVersion() { return "0.0.0"; }
                public String getProgramVersionUnknown() { return "unknown"; }
                public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
            };
        }
    }
}
