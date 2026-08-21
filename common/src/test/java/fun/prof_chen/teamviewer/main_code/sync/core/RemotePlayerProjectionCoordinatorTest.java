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
import fun.prof_chen.teamviewer.main_code.model.LastSeenPlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedLastSeenPlayerRepository;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedRemotePlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void retriesOnlyProjectionsWithPendingNativeCleanup() {
        UUID localId = UUID.randomUUID();
        UUID remoteId = UUID.randomUUID();
        Map<UUID, RemotePlayerInfo> players = new HashMap<>();
        players.put(remoteId, new RemotePlayerInfo(remoteId, new Position3D(4, 65, 8),
                "minecraft:overworld", "Remote"));
        IntegrationRegistry registry = new IntegrationRegistry();
        RetryProjection projection = new RetryProjection();
        register(registry, projection.id(), "plugin.retry", projection);
        AtomicLong clock = new AtomicLong();
        RemotePlayerProjectionCoordinator coordinator = new RemotePlayerProjectionCoordinator(
                registry, ignored -> null, clock::get);
        MapBackedRemotePlayerRepository repository = new MapBackedRemotePlayerRepository(players);
        ClientWorldSnapshot world = new ClientWorldSnapshot(localId, "Local", true,
                "minecraft:overworld", -64, new Position3D(0, 64, 0), new Position3D(0, 65.6, 0),
                new Position3D(0, 0, 1), new Position3D(0, 1, 0), List.of(), List.of());

        coordinator.tick(repository, true, world);
        players.clear();
        projection.failNextEmptySync = true;
        coordinator.tick(repository, true, world);
        assertTrue(projection.needsReconcile());
        int failedSyncCount = projection.syncCount;

        clock.set(500_000_000L);
        coordinator.tick(repository, true, world);
        assertEquals(failedSyncCount, projection.syncCount);
        clock.set(1_000_000_000L);
        coordinator.tick(repository, true, world);
        assertEquals(failedSyncCount + 1, projection.syncCount);
        assertTrue(!projection.needsReconcile());
    }

    @Test
    void filtersLocalLastSeenRecordsAndClearsNativeHistory() {
        UUID localId = UUID.randomUUID();
        UUID remoteId = UUID.randomUUID();
        UUID aliasId = UUID.randomUUID();
        Map<UUID, LastSeenPlayerInfo> history = new HashMap<>();
        history.put(localId, lastSeen(remoteId, "Remote under local key"));
        history.put(aliasId, lastSeen(localId, "Local under alias key"));
        history.put(remoteId, lastSeen(remoteId, "Remote"));
        IntegrationRegistry registry = new IntegrationRegistry();
        LastSeenProjection projection = new LastSeenProjection();
        register(registry, projection.id(), "plugin.last-seen", projection);
        RemotePlayerProjectionCoordinator coordinator = new RemotePlayerProjectionCoordinator(registry);
        ClientWorldSnapshot world = new ClientWorldSnapshot(localId, "Local", true,
                "minecraft:overworld", -64, new Position3D(0, 64, 0), new Position3D(0, 65.6, 0),
                new Position3D(0, 0, 1), new Position3D(0, 1, 0), List.of(), List.of());

        coordinator.tick(new MapBackedRemotePlayerRepository(new HashMap<>()),
                new MapBackedLastSeenPlayerRepository(history), true, true, world);

        assertEquals(Map.of(remoteId, history.get(remoteId)), projection.lastSeen);
        coordinator.clear();
        assertTrue(projection.lastSeen.isEmpty());
        assertTrue(!projection.lastSeenEnabled);
    }

    @Test
    void suppliesLastSeenRelationsAndResyncsWhenTheyChange() {
        UUID localId = UUID.randomUUID();
        UUID remoteId = UUID.randomUUID();
        Map<UUID, LastSeenPlayerInfo> history = Map.of(remoteId, lastSeen(remoteId, "Remote"));
        PlayerRelationView friendly = new PlayerRelationView(PlayerRelation.FRIENDLY, 0xFF123456, true);
        PlayerRelationView enemy = new PlayerRelationView(PlayerRelation.ENEMY, 0xFF654321, true);
        AtomicReference<PlayerRelationView> relation = new AtomicReference<>(friendly);
        IntegrationRegistry registry = new IntegrationRegistry();
        ResolvedLastSeenProjection projection = new ResolvedLastSeenProjection();
        register(registry, projection.id(), "plugin.last-seen-relations", projection);
        RemotePlayerProjectionCoordinator coordinator = new RemotePlayerProjectionCoordinator(registry, ignored -> relation.get());
        ClientWorldSnapshot world = new ClientWorldSnapshot(localId, "Local", true,
                "minecraft:overworld", -64, new Position3D(0, 64, 0), new Position3D(0, 65.6, 0),
                new Position3D(0, 0, 1), new Position3D(0, 1, 0), List.of(), List.of());

        coordinator.tick(new MapBackedRemotePlayerRepository(new HashMap<>()),
                new MapBackedLastSeenPlayerRepository(history), true, true, world);
        assertEquals(friendly, projection.relations.get(remoteId));
        int initialSyncCount = projection.syncCount;

        relation.set(enemy);
        coordinator.tick(new MapBackedRemotePlayerRepository(new HashMap<>()),
                new MapBackedLastSeenPlayerRepository(history), true, true, world);
        assertEquals(initialSyncCount + 1, projection.syncCount);
        assertEquals(enemy, projection.relations.get(remoteId));
    }

    private static LastSeenPlayerInfo lastSeen(UUID id, String name) {
        return new LastSeenPlayerInfo(id, new Position3D(4, 65, 8),
                "minecraft:overworld", name, 1_700_000_004_000L,
                1_700_000_000_000L, 1_700_000_005_000L);
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

    private static final class RetryProjection implements RemotePlayerProjection {
        private int syncCount;
        private boolean failNextEmptySync;
        private boolean pending;

        @Override public String id() { return "retry"; }
        @Override public boolean isAvailable() { return true; }
        @Override public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) { syncCount++; }
        @Override public void syncResolved(Map<UUID, RemotePlayerInfo> players,
                                            Map<UUID, PlayerRelationView> relations,
                                            boolean enabled) {
            syncCount++;
            if (players.isEmpty() && failNextEmptySync) {
                failNextEmptySync = false;
                pending = true;
            } else if (pending) {
                pending = false;
            }
        }
        @Override public boolean needsReconcile() { return pending; }
    }

    private static final class LastSeenProjection implements RemotePlayerProjection {
        private Map<UUID, LastSeenPlayerInfo> lastSeen = Map.of();
        private boolean lastSeenEnabled;

        @Override public String id() { return "last-seen"; }
        @Override public boolean isAvailable() { return true; }
        @Override public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) { }
        @Override public void syncLastSeen(Map<UUID, LastSeenPlayerInfo> players, boolean enabled) {
            lastSeen = Map.copyOf(players);
            lastSeenEnabled = enabled;
        }
    }

    private static final class ResolvedLastSeenProjection implements RemotePlayerProjection {
        private Map<UUID, PlayerRelationView> relations = Map.of();
        private int syncCount;

        @Override public String id() { return "last-seen-resolved"; }
        @Override public boolean isAvailable() { return true; }
        @Override public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) { }
        @Override public void syncLastSeenResolved(
                Map<UUID, LastSeenPlayerInfo> players,
                Map<UUID, PlayerRelationView> relations,
                boolean enabled) {
            this.relations = Map.copyOf(relations);
            syncCount++;
        }
    }
}
