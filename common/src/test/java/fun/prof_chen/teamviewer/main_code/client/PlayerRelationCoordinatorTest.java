package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.api.PlayerRelation;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRole;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.PlayerRelationClassifier;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerRelationCoordinatorTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final List<TabPlayerSnapshot> TAB = List.of(
            new TabPlayerSnapshot(PLAYER.toString(), "Player", "[Town]", "[Town]"));

    @Test
    void mergesAgreementAndResolvesConflictsToNeutral() {
        IntegrationRegistry registry = new IntegrationRegistry();
        register(registry, "first", "plugin.first", PlayerRelation.FRIENDLY);
        register(registry, "second", "plugin.second", PlayerRelation.FRIENDLY);
        PlayerRelationCoordinator coordinator = new PlayerRelationCoordinator(registry);

        coordinator.refresh(TAB);
        assertEquals(PlayerRelation.FRIENDLY, coordinator.relation(PLAYER));

        registry.setPluginRuntime("plugin.second", PluginRuntimeStatus.DISABLED, "");
        register(registry, "third", "plugin.third", PlayerRelation.ENEMY);
        coordinator.refresh(TAB);
        assertEquals(PlayerRelation.NEUTRAL, coordinator.relation(PLAYER));
    }

    @Test
    void filtersUnknownPlayersAndClearsDisabledSourcesAndSessions() {
        UUID unknown = UUID.randomUUID();
        IntegrationRegistry registry = new IntegrationRegistry();
        register(registry, "only", "plugin.only", Map.of(
                PLAYER, PlayerRelation.ENEMY, unknown, PlayerRelation.FRIENDLY));
        PlayerRelationCoordinator coordinator = new PlayerRelationCoordinator(registry);

        coordinator.refresh(TAB);
        assertEquals(Map.of(PLAYER, PlayerRelation.ENEMY), coordinator.snapshot());

        registry.setPluginRuntime("plugin.only", PluginRuntimeStatus.DISABLED, "");
        coordinator.refresh(TAB);
        assertNull(coordinator.relation(PLAYER));

        coordinator.clear();
        assertEquals(Map.of(), coordinator.snapshot());
    }

    private static void register(
            IntegrationRegistry registry, String id, String pluginId, PlayerRelation relation) {
        register(registry, id, pluginId, Map.of(PLAYER, relation));
    }

    private static void register(
            IntegrationRegistry registry, String id, String pluginId, Map<UUID, PlayerRelation> values) {
        PlayerRelationClassifier classifier = new PlayerRelationClassifier() {
            @Override public String id() { return id; }
            @Override public Map<UUID, PlayerRelation> classify(List<TabPlayerSnapshot> players) {
                return values;
            }
        };
        registry.registerNative(new IntegrationCapability(
                id, IntegrationRole.PLAYER_RELATION.id(), IntegrationSupportStatus.AVAILABLE, "",
                pluginId, IntegrationImplementationSource.JAVA_NATIVE, PluginRuntimeStatus.ACTIVE), classifier);
        registry.setPluginRuntime(pluginId, PluginRuntimeStatus.ACTIVE, "");
    }
}
