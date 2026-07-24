package fun.prof_chen.teamviewer.main_code.config.ui;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.abstraction.TransportProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
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

        assertContains(session, ConfigPageId.ROOT,
                ConfigControlId.SERVER_URL, ConfigControlId.ROOM_CODE, ConfigControlId.ALLOW_INSECURE_TLS,
                ConfigControlId.OPEN_DISPLAY, ConfigControlId.OPEN_NETWORK, ConfigControlId.CONNECT,
                ConfigControlId.DISCONNECT, ConfigControlId.CONNECTION_STATUS);
        assertContains(session, ConfigPageId.DISPLAY,
                ConfigControlId.RENDER_DISTANCE, ConfigControlId.SHOW_BOXES, ConfigControlId.SHOW_LINES,
                ConfigControlId.TRACER_START_MODE, ConfigControlId.OPEN_COLOR, ConfigControlId.OPEN_WAYPOINT);
        assertContains(session, ConfigPageId.NETWORK,
                ConfigControlId.UPDATE_INTERVAL, ConfigControlId.UPLOAD_ENTITIES,
                ConfigControlId.BATTLE_MAP_MODE, ConfigControlId.OPEN_PACKET_CAPTURE);
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
        private boolean enabled;

        private FakeControl(Config config) {
            this.config = config;
        }

        public Config getConfig() { return config; }
        public NetworkManager getNetworkManager() { return network; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void reconnect() { }
        public void showActionBar(String message) { }

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
