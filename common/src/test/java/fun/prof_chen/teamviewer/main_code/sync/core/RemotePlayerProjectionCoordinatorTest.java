package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.api.PlayerRelation;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerRelationView;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRole;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedRemotePlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemotePlayerProjectionCoordinatorTest {
    @Test
    void suppliesRelationContextAndKeepsLegacyProjectionSourceCompatible() {
        UUID localId = UUID.randomUUID();
        UUID remoteId = UUID.randomUUID();
        Map<UUID, RemotePlayerInfo> players = new HashMap<>();
        players.put(remoteId, new RemotePlayerInfo(remoteId, new Position3D(4, 65, 8),
                "minecraft:overworld", "Remote"));
        IntegrationRegistry registry = new IntegrationRegistry();
        RelationProjection aware = new RelationProjection("aware");
        LegacyProjection legacy = new LegacyProjection("legacy");
        register(registry, aware.id(), "plugin.aware", aware);
        register(registry, legacy.id(), "plugin.legacy", legacy);
        PlayerRelationView expected = new PlayerRelationView(
                PlayerRelation.ENEMY, 0xFF445566, true);
        RemotePlayerProjectionCoordinator coordinator = new RemotePlayerProjectionCoordinator(
                registry, ignored -> expected);
        assertEquals(2, registry.activeRemotePlayerProjections().size());

        coordinator.tick(new MapBackedRemotePlayerRepository(players), true,
                new ClientWorldSnapshot(localId, "Local", true, "minecraft:overworld", -64,
                        new Position3D(0, 64, 0), new Position3D(0, 65.6, 0),
                        new Position3D(0, 0, 1), new Position3D(0, 1, 0), List.of(), List.of()));

        assertEquals(expected, aware.relations.get(remoteId));
        assertTrue(legacy.synced);
        int relationSyncs = aware.syncCount;
        int legacySyncs = legacy.syncCount;
        for (int tick = 0; tick < 20; tick++) {
            coordinator.tick(new MapBackedRemotePlayerRepository(players), true,
                    new ClientWorldSnapshot(localId, "Local", true, "minecraft:overworld", -64,
                            new Position3D(0, 64, 0), new Position3D(0, 65.6, 0),
                            new Position3D(0, 0, 1), new Position3D(0, 1, 0), List.of(), List.of()));
        }
        assertEquals(relationSyncs, aware.syncCount);
        assertEquals(legacySyncs, legacy.syncCount);
        players.put(remoteId, new RemotePlayerInfo(remoteId, new Position3D(5, 65, 8),
                "minecraft:overworld", "Remote"));
        coordinator.tick(new MapBackedRemotePlayerRepository(players), true,
                new ClientWorldSnapshot(localId, "Local", true, "minecraft:overworld", -64,
                        new Position3D(0, 64, 0), new Position3D(0, 65.6, 0),
                        new Position3D(0, 0, 1), new Position3D(0, 1, 0), List.of(), List.of()));
        assertTrue(aware.syncCount > relationSyncs);
    }

    private static void register(
            IntegrationRegistry registry, String id, String pluginId, RemotePlayerProjection projection) {
        registry.registerNative(new IntegrationCapability(
                id, IntegrationRole.REMOTE_PLAYER.id(), IntegrationSupportStatus.AVAILABLE, "",
                pluginId, IntegrationImplementationSource.JAVA_NATIVE, PluginRuntimeStatus.ACTIVE), projection);
        registry.setPluginRuntime(pluginId, PluginRuntimeStatus.ACTIVE, "");
    }

    private static final class RelationProjection implements RemotePlayerProjection {
        private final String id;
        private Map<UUID, PlayerRelationView> relations = Map.of();
        private int syncCount;

        private RelationProjection(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public boolean isAvailable() { return true; }
        @Override public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) { }
        @Override public void syncResolved(
                Map<UUID, RemotePlayerInfo> players,
                Map<UUID, PlayerRelationView> relations,
                boolean enabled) {
            this.relations = relations;
            syncCount++;
        }
    }

    private static final class LegacyProjection implements RemotePlayerProjection {
        private final String id;
        private boolean synced;
        private int syncCount;

        private LegacyProjection(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public boolean isAvailable() { return true; }
        @Override public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
            synced = true;
            syncCount++;
        }
    }
}
