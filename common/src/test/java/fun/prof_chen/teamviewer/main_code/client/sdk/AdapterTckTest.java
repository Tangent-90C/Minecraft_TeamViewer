package fun.prof_chen.teamviewer.main_code.client.sdk;

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
import fun.prof_chen.teamviewer.main_code.config.ui.ClientUiSession;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdapterTckTest {
    @Test
    void acceptsACompleteUnavailableTitleScreenAdapter() {
        ClientEventBridge<Object, Object> events = new ClientEventBridge<>() {
            @Override
            public void register(ClientEventHandler<Object, Object> handler) { }
            @Override
            public Set<ClientEventType> registeredEvents() { return EnumSet.allOf(ClientEventType.class); }
        };
        ClientAdapterBundle<Object, Object> adapters = new ClientAdapterBundle<>(
                "test", runtime(), game(), events, (context, frame) -> { }, (context, frame) -> { },
                controller -> { }, completeRegistry());

        AdapterTckReport report = AdapterTck.inspect(adapters, configUi());

        assertTrue(report.passed(), report.issues().toString());
        assertTrue(report.toJson().contains("\"passed\":true"));
        assertTrue(report.toJson().contains("\"modVersion\":\"test\""));
        assertTrue(report.toJson().contains("\"minecraftVersion\":\"test-minecraft\""));
        assertTrue(report.toJson().contains("\"javaVersion\":"));
    }

    @Test
    void reportsFailedOptionalIntegrationWithoutFailingCoreTck() {
        ClientEventBridge<Object, Object> events = new ClientEventBridge<>() {
            @Override
            public void register(ClientEventHandler<Object, Object> handler) { }
            @Override
            public Set<ClientEventType> registeredEvents() { return EnumSet.allOf(ClientEventType.class); }
        };
        IntegrationRegistry registry = completeRegistry();
        registry.declare("optional-map", IntegrationRole.SHARED_WAYPOINT.id(), "test.optional",
                "Optional Map", IntegrationSupportStatus.FAILED, "initialization failed");
        ClientAdapterBundle<Object, Object> adapters = new ClientAdapterBundle<>(
                "test", runtime(), game(), events, (context, frame) -> { }, (context, frame) -> { },
                controller -> { }, registry);

        AdapterTckReport report = AdapterTck.inspect(adapters, configUi());

        assertTrue(report.passed(), report.issues().toString());
        assertTrue(report.toJson().contains("\"status\":\"FAILED\""));
    }

    @Test
    void failsWhenAnExpectedCapabilityIsNotRegistered() {
        ClientEventBridge<Object, Object> events = new ClientEventBridge<>() {
            @Override
            public void register(ClientEventHandler<Object, Object> handler) { }
            @Override
            public Set<ClientEventType> registeredEvents() { return EnumSet.allOf(ClientEventType.class); }
        };
        IntegrationRegistry missingXaeroWorldMap = completeRegistry();
        missingXaeroWorldMap = registryWithout(missingXaeroWorldMap, IntegrationIds.XAERO_WORLDMAP);
        ClientAdapterBundle<Object, Object> adapters = new ClientAdapterBundle<>(
                "test", runtime(), game(), events, (context, frame) -> { }, (context, frame) -> { },
                controller -> { }, missingXaeroWorldMap);

        AdapterTckReport report = AdapterTck.inspect(adapters, configUi());

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.contains("missing expected capability " + IntegrationIds.XAERO_WORLDMAP)), report.issues().toString());
    }

    private static RuntimeGateway runtime() {
        return new RuntimeGateway() {
            @Override
            public String getCurrentDimensionId() { return null; }
            @Override
            public UUID getLocalPlayerId() { return null; }
            @Override
            public String getClientProgramVersion() { return "test"; }
            @Override
            public String getMinecraftVersion() { return "test-minecraft"; }
            @Override
            public String getClientProtocolVersion() { return "test"; }
            @Override
            public String getClientMinCompatibleProtocolVersion() { return "test"; }
            @Override
            public String getServerProtocolFallbackVersion() { return "test"; }
            @Override
            public String getProgramVersionUnknown() { return "unknown"; }
            @Override
            public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
        };
    }

    private static GameClientBridge game() {
        return new GameClientBridge() {
            @Override
            public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) { return ClientReportSnapshot.unavailable(); }
            @Override
            public List<fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot> captureTabPlayerSnapshot() {
                return List.of();
            }
            @Override
            public ClientWorldSnapshot captureWorldSnapshot(boolean includeEntities) {
                return ClientWorldSnapshot.unavailable();
            }
            @Override
            public ScoreboardSnapshot captureScoreboardSnapshot() { return ScoreboardSnapshot.unavailable(); }
            @Override
            public Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance) { return Optional.empty(); }
            @Override
            public Optional<Position3D> resolveEntityPosition(String id, String name, String dimension) { return Optional.empty(); }
            @Override
            public boolean isEntityDead(String entityId) { return false; }
            @Override
            public boolean isMiddleMouseButtonDown() { return false; }
            @Override
            public boolean isGameplayInputAvailable() { return false; }
            @Override
            public void showActionBar(String message) { }
        };
    }

    private static IntegrationRegistry completeRegistry() {
        IntegrationRegistry registry = new IntegrationRegistry();
        IntegrationIds.expectedRoles().forEach((id, role) -> registry.declare(
                id, role, IntegrationIds.pluginIdForCapability(id), id,
                IntegrationSupportStatus.MOD_NOT_INSTALLED, "not installed"));
        return registry;
    }

    private static IntegrationRegistry registryWithout(IntegrationRegistry source, String excludedId) {
        IntegrationRegistry result = new IntegrationRegistry();
        source.capabilities().stream()
                .filter(capability -> !capability.id().equals(excludedId))
                .forEach(capability -> result.declare(
                        capability.id(), capability.role(), capability.pluginId(), capability.displayName(),
                        capability.status(), capability.detail()));
        return result;
    }

    private static ClientUiSession configUi() {
        ConfigUiController config = new ConfigUiController() {
            @Override
            public ConfigPageView page(ConfigPageId pageId, int width, int height) {
                return new ConfigPageView(pageId, UiText.literal(pageId.name()), 10,
                        List.of(ConfigControlView.button(ConfigControlId.BACK,
                                new UiRect(0, 0, 10, 10), UiText.literal("back"), null, true)));
            }
            @Override
            public void setText(ConfigControlId id, String value) { }
            @Override
            public void setChecked(ConfigControlId id, boolean checked) { }
            @Override
            public ConfigUiAction activate(ConfigPageId currentPage, ConfigControlId id) { return ConfigUiAction.stay(); }
            @Override
            public ConfigUiAction close(ConfigPageId pageId) { return ConfigUiAction.stay(); }
        };
        PluginManagerUiController plugins = new PluginManagerUiController() {
            @Override
            public PluginManagerView view(int width, int height) {
                UiRect bounds = new UiRect(0, 0, width, height);
                return new PluginManagerView(
                        bounds, bounds, bounds, null, null, null, null,
                        bounds, bounds, false, false,
                        PluginManagerView.PluginManagerTab.INSTALLED,
                        List.of(), null, null, null);
            }

            @Override
            public ConfigUiAction activate(ConfigControlId id) { return ConfigUiAction.stay(); }
            @Override
            public void setText(ConfigControlId id, String value) { }
            @Override
            public void scrollList(int rows) { }
            @Override
            public void scrollDetail(int pixels) { }
            @Override
            public void moveSelection(int rows) { }
            @Override
            public void commitTextSettings() { }
        };
        return new ClientUiSession(config, plugins);
    }
}
