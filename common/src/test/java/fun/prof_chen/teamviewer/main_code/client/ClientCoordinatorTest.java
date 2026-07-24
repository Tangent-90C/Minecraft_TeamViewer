package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.abstraction.TransportProcess;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointUpdateListener;
import fun.prof_chen.teamviewer.main_code.sync.core.SharedWaypointSyncCoordinator;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedSharedWaypointRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCoordinatorTest {
    @Test
    void sendsVersionNeutralSnapshotsAtNegotiatedInterval() {
        Config config = new Config();
        config.setUpdateInterval(20);
        config.setUploadEntities(true);
        RecordingNetworkManager network = new RecordingNetworkManager();
        network.connected = true;
        network.negotiatedInterval = 2;
        UUID localId = UUID.randomUUID();
        FakeGameClientBridge game = new FakeGameClientBridge(snapshot(localId));
        ClientCoordinator coordinator = new ClientCoordinator(config, network, game);

        coordinator.setEnabled(true);
        coordinator.onEndClientTick();
        assertEquals(0, network.playerReports.size());

        coordinator.onEndClientTick();
        assertEquals(1, network.playerReports.size());
        assertEquals(localId, network.lastSubmitPlayerId);
        assertEquals("local", network.playerReports.getFirst().get(localId).get("playerName"));
        assertEquals("minecraft:overworld", network.entityReports.getFirst().get("entity-1").get("dimension"));
        assertEquals("local", network.tabReports.getFirst().getFirst().get("name"));
        assertEquals(1, game.captureCount);
    }

    @Test
    void lifecycleDelegatesConnectionWithoutMinecraftTypes() {
        Config config = new Config();
        RecordingNetworkManager network = new RecordingNetworkManager();
        ClientCoordinator coordinator = new ClientCoordinator(config, network, new FakeGameClientBridge(ClientReportSnapshot.unavailable()));

        coordinator.setEnabled(true);
        assertTrue(coordinator.isEnabled());
        assertEquals(1, network.connectCount);

        coordinator.reconnect();
        assertEquals(1, network.disconnectCount);
        assertEquals(2, network.connectCount);

        coordinator.onLeftPlaySession();
        assertFalse(coordinator.isEnabled());
        assertEquals(2, network.disconnectCount);
    }

    @Test
    void createsQuickMarkThroughVersionNeutralTargetSnapshot() {
        Config config = new Config();
        RecordingNetworkManager network = new RecordingNetworkManager();
        network.connected = true;
        UUID localId = UUID.randomUUID();
        FakeGameClientBridge game = new FakeGameClientBridge(
                snapshot(localId),
                new EntityTargetSnapshot(new Position3D(12.8, 64.2, -3.1), null, null, null, false, false));
        ClientCoordinator coordinator = new ClientCoordinator(config, network, game);
        Map<String, SharedWaypointInfo> waypoints = new HashMap<>();
        MapBackedSharedWaypointRepository repository = new MapBackedSharedWaypointRepository(waypoints);
        RecordingWaypointGateway gateway = new RecordingWaypointGateway();
        SharedWaypointSyncCoordinator waypointCoordinator = new SharedWaypointSyncCoordinator(
                repository, gateway, List.of());
        coordinator.configureWaypointSupport(repository, waypointCoordinator);
        coordinator.setEnabled(true);

        assertTrue(coordinator.createQuickMark());
        assertEquals(1, waypoints.size());
        SharedWaypointInfo waypoint = waypoints.values().iterator().next();
        assertEquals("block", waypoint.targetType());
        assertEquals(12, waypoint.x());
        assertEquals(-4, waypoint.z());
        assertEquals(localId, gateway.submitPlayerId);
        assertEquals(1, gateway.upserts.size());
    }

    private static ClientReportSnapshot snapshot(UUID localId) {
        PlayerSnapshot player = new PlayerSnapshot(
                localId,
                new Position3D(1, 2, 3),
                new Position3D(0.1, 0.2, 0.3),
                "minecraft:overworld",
                "local",
                20,
                20,
                0,
                false,
                0.6f,
                1.8f);
        EntitySnapshot entity = new EntitySnapshot(
                "entity-1",
                new Position3D(4, 5, 6),
                new Position3D(0, 0, 0),
                "minecraft:overworld",
                "minecraft:zombie",
                null,
                0.6f,
                1.95f);
        return new ClientReportSnapshot(
                localId,
                true,
                "minecraft:overworld",
                List.of(player),
                List.of(entity),
                List.of(new TabPlayerSnapshot(localId.toString(), "local", "team", "team")));
    }

    private static final class FakeGameClientBridge implements GameClientBridge {
        private final ClientReportSnapshot snapshot;
        private final EntityTargetSnapshot target;
        private int captureCount;

        private FakeGameClientBridge(ClientReportSnapshot snapshot) {
            this(snapshot, null);
        }

        private FakeGameClientBridge(ClientReportSnapshot snapshot, EntityTargetSnapshot target) {
            this.snapshot = snapshot;
            this.target = target;
        }

        @Override
        public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) {
            captureCount++;
            return snapshot;
        }

        @Override
        public Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance) {
            return Optional.ofNullable(target);
        }

        @Override
        public Optional<Position3D> resolveEntityPosition(String entityId, String entityName, String dimensionId) {
            return Optional.empty();
        }

        @Override
        public boolean isEntityDead(String entityId) {
            return false;
        }

        @Override
        public boolean isMiddleMouseButtonDown() {
            return false;
        }

        @Override
        public void showActionBar(String message) {
        }
    }

    private static final class RecordingWaypointGateway
            implements fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway {
        private UUID submitPlayerId;
        private Map<String, WaypointSyncPayload> upserts = Map.of();

        public boolean isConnected() { return true; }
        public void addWaypointUpdateListener(WaypointUpdateListener listener) { }
        public void removeWaypointUpdateListener(WaypointUpdateListener listener) { }
        public void sendWaypointUpserts(UUID submitPlayerId, Map<String, WaypointSyncPayload> payloads) {
            this.submitPlayerId = submitPlayerId;
            this.upserts = Map.copyOf(payloads);
        }
        public void sendWaypointDeletes(UUID submitPlayerId, List<String> waypointIds) { }
        public void sendWaypointEntityDeathCancel(UUID submitPlayerId, List<String> targetEntityIds) { }
        public Position3D getRemoteEntityPosition(String entityId, String expectedDimension) { return null; }
        public Position3D getRemotePlayerPosition(String playerId, String playerName, String expectedDimension) { return null; }
    }

    private static final class RecordingNetworkManager extends NetworkManager {
        private boolean connected;
        private int negotiatedInterval = 1;
        private int connectCount;
        private int disconnectCount;
        private UUID lastSubmitPlayerId;
        private final List<Map<UUID, Map<String, Object>>> playerReports = new ArrayList<>();
        private final List<Map<String, Map<String, Object>>> entityReports = new ArrayList<>();
        private final List<List<Map<String, Object>>> tabReports = new ArrayList<>();

        private RecordingNetworkManager() {
            super(new HashMap<UUID, RemotePlayerInfo>(), runtime(), noTransport());
        }

        @Override
        public void connect() {
            connectCount++;
        }

        @Override
        public void disconnect() {
            disconnectCount++;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public int getNegotiatedReportIntervalTicks() {
            return negotiatedInterval;
        }

        @Override
        public void sendPlayersUpdate(UUID submitPlayerId, Map<UUID, Map<String, Object>> players) {
            lastSubmitPlayerId = submitPlayerId;
            playerReports.add(players);
        }

        @Override
        public void sendEntitiesUpdate(UUID submitPlayerId, Map<String, Map<String, Object>> entities) {
            entityReports.add(entities);
        }

        @Override
        public void sendTabPlayersUpdate(UUID submitPlayerId, List<Map<String, Object>> tabPlayers) {
            tabReports.add(tabPlayers);
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

        private static TransportProcess noTransport() {
            return (uri, options, listener) -> null;
        }
    }
}
