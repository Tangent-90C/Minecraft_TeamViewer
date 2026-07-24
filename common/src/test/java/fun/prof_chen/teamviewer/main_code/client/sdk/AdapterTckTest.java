package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapNativeBridge;
import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlView;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageView;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiAction;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiController;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableSharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterTckTest {
    @Test
    void acceptsACompleteUnavailableTitleScreenAdapter() {
        ClientEventBridge<Object, Object> events = new ClientEventBridge<>() {
            public void register(ClientEventHandler<Object, Object> handler) { }
            public Set<ClientEventType> registeredEvents() { return EnumSet.allOf(ClientEventType.class); }
        };
        ClientAdapterBundle<Object, Object> adapters = new ClientAdapterBundle<>(
                "test", runtime(), game(), events, (context, frame) -> { }, (context, frame) -> { },
                controller -> { }, battleMap(), new MapAdapterBundle(List.of(), List.of()));

        AdapterTckReport report = AdapterTck.inspect(adapters, configUi());

        assertTrue(report.passed(), report.issues().toString());
        assertTrue(report.toJson().contains("\"passed\":true"));
    }

    @Test
    void reportsFailedOptionalIntegrationWithoutFailingCoreTck() {
        ClientEventBridge<Object, Object> events = new ClientEventBridge<>() {
            public void register(ClientEventHandler<Object, Object> handler) { }
            public Set<ClientEventType> registeredEvents() { return EnumSet.allOf(ClientEventType.class); }
        };
        MapAdapterBundle maps = new MapAdapterBundle(List.of(), List.of(
                new UnavailableSharedWaypointMapAdapter(
                        "optional-map", IntegrationSupportStatus.FAILED, "initialization failed")));
        ClientAdapterBundle<Object, Object> adapters = new ClientAdapterBundle<>(
                "test", runtime(), game(), events, (context, frame) -> { }, (context, frame) -> { },
                controller -> { }, battleMap(), maps);

        AdapterTckReport report = AdapterTck.inspect(adapters, configUi());

        assertTrue(report.passed(), report.issues().toString());
        assertTrue(report.toJson().contains("\"status\":\"FAILED\""));
    }

    private static RuntimeGateway runtime() {
        return new RuntimeGateway() {
            public String getCurrentDimensionId() { return null; }
            public UUID getLocalPlayerId() { return null; }
            public String getClientProgramVersion() { return "test"; }
            public String getClientProtocolVersion() { return "test"; }
            public String getClientMinCompatibleProtocolVersion() { return "test"; }
            public String getServerProtocolFallbackVersion() { return "test"; }
            public String getProgramVersionUnknown() { return "unknown"; }
            public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
        };
    }

    private static GameClientBridge game() {
        return new GameClientBridge() {
            public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) { return ClientReportSnapshot.unavailable(); }
            public ClientWorldSnapshot captureWorldSnapshot() { return ClientWorldSnapshot.unavailable(); }
            public ScoreboardSnapshot captureScoreboardSnapshot() { return ScoreboardSnapshot.unavailable(); }
            public Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance) { return Optional.empty(); }
            public Optional<Position3D> resolveEntityPosition(String id, String name, String dimension) { return Optional.empty(); }
            public boolean isEntityDead(String entityId) { return false; }
            public boolean isMiddleMouseButtonDown() { return false; }
            public boolean isGameplayInputAvailable() { return false; }
            public void showActionBar(String message) { }
        };
    }

    private static BattleMapNativeBridge battleMap() {
        return new BattleMapNativeBridge() {
            public boolean isAvailable() { return false; }
            public String unavailableReason() { return "not installed"; }
            public Optional<fun.prof_chen.teamviewer.main_code.battlemap.NativeBattleMapSnapshot> capture() { return Optional.empty(); }
        };
    }

    private static ConfigUiController configUi() {
        return new ConfigUiController() {
            public ConfigPageView page(ConfigPageId pageId, int width, int height) {
                return new ConfigPageView(pageId, UiText.literal(pageId.name()), 10,
                        List.of(ConfigControlView.button(ConfigControlId.BACK,
                                new UiRect(0, 0, 10, 10), UiText.literal("back"), null, true)));
            }
            public void setText(ConfigControlId id, String value) { }
            public void setChecked(ConfigControlId id, boolean checked) { }
            public ConfigUiAction activate(ConfigPageId currentPage, ConfigControlId id) { return ConfigUiAction.stay(); }
            public ConfigUiAction close(ConfigPageId pageId) { return ConfigUiAction.stay(); }
        };
    }
}
