package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointUpdateListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SharedWaypointMapSyncPolicyTest {
    private static final UUID LOCAL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMOTE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void ownsLocalDiffAndRemoteProjectionDeletion() {
        Config config = new Config();
        config.setUploadSharedWaypoints(true);
        AtomicLong clock = new AtomicLong(2_000L);
        FakeGateway gateway = new FakeGateway();
        FakeAdapter adapter = new FakeAdapter();
        adapter.local = List.of(new NativeMapWaypointSnapshot("native", "Home", "H", 10, 64, 20,
                "minecraft:overworld", 0xFF00FF00));
        SharedWaypointMapSyncPolicy policy = new SharedWaypointMapSyncPolicy(config, new FakeGame(), gateway, clock::get);
        SharedWaypointInfo remote = new SharedWaypointInfo("remote-waypoint", REMOTE, "Alice", "Target", "!",
                30, 70, 40, "minecraft:overworld", 0xFFFF0000, 1L,
                null, null, null, null, "quick", null, null);

        ClientWorldSnapshot world = new FakeGame().captureWorldSnapshot(false);
        policy.tick(List.of(adapter), Map.of(remote.waypointId(), remote), true, world);
        assertEquals(1, adapter.upserts.size());
        assertEquals("[TV] Alice: Target", adapter.upserts.get(0).name());
        assertEquals(0, gateway.upsertCount); // first local scan establishes the no-upload baseline

        adapter.local = List.of();
        clock.set(4_000L);
        policy.tick(List.of(adapter), Map.of(), true, world);
        assertFalse(gateway.deletedIds.isEmpty());
        assertEquals(List.of("remote-waypoint"), adapter.deletedIds);
    }

    private static final class FakeAdapter implements SharedWaypointMapAdapter {
        private List<NativeMapWaypointSnapshot> local = List.of();
        private final List<MapWaypointCommand> upserts = new ArrayList<>();
        private final List<String> deletedIds = new ArrayList<>();
        public String id() { return "journeymap-test"; }
        public boolean isAvailable() { return true; }
        public List<NativeMapWaypointSnapshot> listLocalWaypoints() { return local; }
        public void upsertRemoteWaypoint(MapWaypointCommand command) { upserts.add(command); }
        public void deleteRemoteWaypoint(String waypointId) { deletedIds.add(waypointId); }
        public void clearRemoteWaypoints() { }
    }

    private static final class FakeGateway implements WaypointSyncGateway {
        private int upsertCount;
        private List<String> deletedIds = List.of();
        public boolean isConnected() { return true; }
        public void addWaypointUpdateListener(WaypointUpdateListener listener) { }
        public void removeWaypointUpdateListener(WaypointUpdateListener listener) { }
        public void sendWaypointUpserts(UUID submitPlayerId, Map<String, WaypointSyncPayload> payloads) {
            upsertCount += payloads.size();
        }
        public void sendWaypointDeletes(UUID submitPlayerId, List<String> waypointIds) { deletedIds = List.copyOf(waypointIds); }
        public void sendWaypointEntityDeathCancel(UUID submitPlayerId, List<String> targetEntityIds) { }
        public Position3D getRemoteEntityPosition(String entityId, String expectedDimension) { return null; }
        public Position3D getRemotePlayerPosition(String playerId, String playerName, String expectedDimension) { return null; }
    }

    private static final class FakeGame implements GameClientBridge {
        public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) { return ClientReportSnapshot.unavailable(); }
        public List<fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot> captureTabPlayerSnapshot() {
            return List.of();
        }
        public ClientWorldSnapshot captureWorldSnapshot(boolean includeEntities) {
            Position3D position = new Position3D(0, 64, 0);
            return new ClientWorldSnapshot(LOCAL, "Local", true, "minecraft:overworld", -64,
                    position, position, new Position3D(0, 0, 1), new Position3D(0, 1, 0), List.of(), List.of());
        }
        public ScoreboardSnapshot captureScoreboardSnapshot() { return ScoreboardSnapshot.unavailable(); }
        public Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance) { return Optional.empty(); }
        public Optional<Position3D> resolveEntityPosition(String entityId, String entityName, String dimensionId) { return Optional.empty(); }
        public boolean isEntityDead(String entityId) { return false; }
        public boolean isMiddleMouseButtonDown() { return false; }
        public boolean isGameplayInputAvailable() { return true; }
        public void showActionBar(String message) { }
    }
}
