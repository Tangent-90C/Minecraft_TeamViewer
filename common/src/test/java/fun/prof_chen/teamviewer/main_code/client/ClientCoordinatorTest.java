package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.entity.EntityCaptureTarget;
import fun.prof_chen.teamviewer.main_code.client.entity.EntityUploadFilter;
import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
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
import fun.prof_chen.teamviewer.main_code.network.protocol.EntityPatchView;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointUpdateListener;
import fun.prof_chen.teamviewer.main_code.sync.core.SharedWaypointSyncCoordinator;
import fun.prof_chen.teamviewer.main_code.sync.core.RemotePlayerProjectionCoordinator;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedRemotePlayerRepository;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedSharedWaypointRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCoordinatorTest {
    @Test
    void renderSkipsSnapshotsWithoutVisibleConsumersAndRequestsEntitiesOnlyWhenNeeded() {
        Config config = new Config();
        RecordingNetworkManager network = new RecordingNetworkManager();
        UUID localId = UUID.randomUUID();
        FakeGameClientBridge game = new FakeGameClientBridge(snapshot(localId));
        ClientCoordinator coordinator = new ClientCoordinator(config, network, game);
        Map<String, SharedWaypointInfo> waypoints = new HashMap<>();
        MapBackedSharedWaypointRepository waypointRepository = new MapBackedSharedWaypointRepository(waypoints);
        RecordingWaypointGateway gateway = new RecordingWaypointGateway();
        fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry integrations =
                new fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry();
        SharedWaypointSyncCoordinator waypointCoordinator = new SharedWaypointSyncCoordinator(
                waypointRepository, gateway, integrations, config, game);
        coordinator.configureRuntimeSupport(
                new MapBackedRemotePlayerRepository(new HashMap<>()),
                waypointRepository, waypointCoordinator, gateway,
                new RemotePlayerProjectionCoordinator(integrations));
        coordinator.setEnabled(true);

        coordinator.buildWorldRenderFrame();
        assertEquals(0, game.worldCaptureCount);

        config.setShowBoxes(true);
        coordinator.buildWorldRenderFrame();
        assertEquals(1, game.worldCaptureCount);
        assertEquals(0, game.worldEntityCaptureCount);

        config.setShowBoxes(false);
        waypoints.put("entity-waypoint", new SharedWaypointInfo(
                "entity-waypoint", UUID.randomUUID(), "Remote", "Target", "!",
                1, 2, 3, "minecraft:overworld", 0xFFFFFFFF, 1L,
                "entity", UUID.randomUUID().toString(), "minecraft:player", "Target",
                "quick", null, null));
        coordinator.buildWorldRenderFrame();
        assertEquals(2, game.worldCaptureCount);
        assertEquals(1, game.worldEntityCaptureCount);
    }

    @Test
    void samplesLargeTabListAtMostOncePerSecond() {
        Config config = new Config();
        config.setUploadEntities(false);
        RecordingNetworkManager network = new RecordingNetworkManager();
        network.connected = true;
        network.negotiatedInterval = 1;
        UUID localId = UUID.randomUUID();
        List<TabPlayerSnapshot> tabPlayers = java.util.stream.IntStream.range(0, 800)
                .mapToObj(index -> new TabPlayerSnapshot(
                        UUID.nameUUIDFromBytes(("tab-" + index).getBytes()).toString(),
                        "Player" + index, "team", "[T]"))
                .toList();
        FakeGameClientBridge game = new FakeGameClientBridge(snapshot(localId), null, tabPlayers);
        ClientCoordinator coordinator = new ClientCoordinator(config, network, game);

        coordinator.setEnabled(true);
        for (int tick = 0; tick < 100; tick++) {
            coordinator.onEndClientTick();
        }

        assertEquals(100, game.captureCount, "movement report remains independently negotiated");
        assertEquals(100, game.worldCaptureCount, "tick consumers share one lightweight world snapshot");
        assertEquals(0, game.worldEntityCaptureCount);
        assertEquals(5, game.tabCaptureCount, "initial capture plus one capture every 20 ticks");
        assertEquals(5, network.tabReports.size());
        assertTrue(network.tabReports.stream().allMatch(value -> value.size() == 800));

        network.connected = false;
        coordinator.onEndClientTick();
        network.connected = true;
        coordinator.onEndClientTick();
        assertEquals(6, game.tabCaptureCount, "reconnection triggers an immediate fresh Tab snapshot");
        assertEquals(6, network.tabReports.size());
    }

    @Test
    void sendsVersionNeutralSnapshotsAtNegotiatedInterval() throws Exception {
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
        assertEquals(1, network.tabReports.size());

        coordinator.onEndClientTick();
        for (int attempt = 0; attempt < 100 && network.entityReports.isEmpty(); attempt++) {
            Thread.sleep(10L);
        }
        assertEquals(1, network.playerReports.size());
        assertEquals(localId, network.lastSubmitPlayerId);
        assertEquals("local", network.playerReports.get(0).get(localId).get("playerName"));
        assertEquals("minecraft:overworld",
                network.entityReports.get(0).values().iterator().next().get("dimension"));
        assertEquals("local", network.tabReports.get(0).get(0).get("name"));
        assertEquals(1, game.captureCount);
        assertEquals(2, game.worldCaptureCount);
        assertEquals(1, game.tabCaptureCount);
    }

    @Test
    void adaptiveEntityCadenceCapsLargeWorldAtOncePerSecond() {
        Config config = new Config();
        config.setUploadEntities(true);
        config.setEntityReportMode(Config.ENTITY_REPORT_AUTO);
        RecordingNetworkManager network = new RecordingNetworkManager();
        network.connected = true;
        network.negotiatedInterval = 2;
        FakeGameClientBridge game = new FakeGameClientBridge(snapshot(UUID.randomUUID()));
        game.simulatedEntityCount = 1500;
        ClientCoordinator coordinator = new ClientCoordinator(config, network, game);

        coordinator.setEnabled(true);
        for (int tick = 0; tick < 100; tick++) coordinator.onEndClientTick();

        assertEquals(5, game.entityFrameCaptureCount,
                "first capture is immediate, then 1500 loaded entities use a 20-tick interval");
        coordinator.shutdown();
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
                repository, gateway, new fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry(),
                config, game);
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
                UUID.nameUUIDFromBytes("entity-1".getBytes()).toString(),
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
                List.of(entity));
    }

    private static final class FakeGameClientBridge implements GameClientBridge {
        private final ClientReportSnapshot snapshot;
        private final EntityTargetSnapshot target;
        private final List<TabPlayerSnapshot> tabPlayers;
        private int captureCount;
        private int worldCaptureCount;
        private int worldEntityCaptureCount;
        private int tabCaptureCount;
        private int entityFrameCaptureCount;
        private int simulatedEntityCount = -1;

        private FakeGameClientBridge(ClientReportSnapshot snapshot) {
            this(snapshot, null, snapshot.localPlayerId() == null ? List.of() : List.of(
                    new TabPlayerSnapshot(snapshot.localPlayerId().toString(), "local", "team", "team")));
        }

        private FakeGameClientBridge(ClientReportSnapshot snapshot, EntityTargetSnapshot target) {
            this(snapshot, target, snapshot.localPlayerId() == null ? List.of() : List.of(
                    new TabPlayerSnapshot(snapshot.localPlayerId().toString(), "local", "team", "team")));
        }

        private FakeGameClientBridge(
                ClientReportSnapshot snapshot, EntityTargetSnapshot target, List<TabPlayerSnapshot> tabPlayers) {
            this.snapshot = snapshot;
            this.target = target;
            this.tabPlayers = List.copyOf(tabPlayers);
        }

        @Override
        public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) {
            captureCount++;
            return snapshot;
        }

        @Override
        public void captureEntityFrame(EntityCaptureTarget target, EntityUploadFilter filter) {
            entityFrameCaptureCount++;
            target.begin(snapshot.localPlayerId(), snapshot.dimension(), 0);
            for (EntitySnapshot entity : snapshot.entities()) {
                if (!filter.allows(entity.type(), entity.name())) continue;
                target.accept(
                        UUID.fromString(entity.id()),
                        entity.position().x(), entity.position().y(), entity.position().z(),
                        entity.velocity().x(), entity.velocity().y(), entity.velocity().z(),
                        entity.type(), entity.name(), entity.width(), entity.height());
            }
            target.finish(simulatedEntityCount >= 0 ? simulatedEntityCount : snapshot.entities().size());
        }

        @Override
        public List<TabPlayerSnapshot> captureTabPlayerSnapshot() {
            tabCaptureCount++;
            return tabPlayers;
        }

        @Override
        public ClientWorldSnapshot captureWorldSnapshot(boolean includeEntities) {
            worldCaptureCount++;
            if (includeEntities) worldEntityCaptureCount++;
            if (snapshot.localPlayerId() == null) return ClientWorldSnapshot.unavailable();
            Position3D position = new Position3D(1, 2, 3);
            return new ClientWorldSnapshot(
                    snapshot.localPlayerId(), "local", snapshot.localPlayerAlive(), snapshot.dimension(), -64,
                    position, position, new Position3D(0, 0, 1), new Position3D(0, 1, 0),
                    snapshot.players(), includeEntities ? snapshot.entities() : List.of());
        }

        @Override
        public fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot captureScoreboardSnapshot() {
            return fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot.unavailable();
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
        public boolean isGameplayInputAvailable() {
            return true;
        }

        @Override
        public void showActionBar(String message) {
        }
    }

    private static final class RecordingWaypointGateway
            implements fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway {
        private UUID submitPlayerId;
        private Map<String, WaypointSyncPayload> upserts = Map.of();

        @Override
        public boolean isConnected() { return true; }
        @Override
        public void addWaypointUpdateListener(WaypointUpdateListener listener) { }
        @Override
        public void removeWaypointUpdateListener(WaypointUpdateListener listener) { }
        @Override
        public void sendWaypointUpserts(UUID submitPlayerId, Map<String, WaypointSyncPayload> payloads) {
            this.submitPlayerId = submitPlayerId;
            this.upserts = Map.copyOf(payloads);
        }
        @Override
        public void sendWaypointDeletes(UUID submitPlayerId, List<String> waypointIds) { }
        @Override
        public void sendWaypointEntityDeathCancel(UUID submitPlayerId, List<String> targetEntityIds) { }
        @Override
        public Position3D getRemoteEntityPosition(String entityId, String expectedDimension) { return null; }
        @Override
        public Position3D getRemotePlayerPosition(String playerId, String playerName, String expectedDimension) { return null; }
    }

    private static final class RecordingNetworkManager extends NetworkManager {
        private boolean connected;
        private int negotiatedInterval = 1;
        private int connectCount;
        private int disconnectCount;
        private UUID lastSubmitPlayerId;
        private final List<Map<UUID, Map<String, Object>>> playerReports = new ArrayList<>();
        private final List<Map<String, Map<String, Object>>> entityReports = new CopyOnWriteArrayList<>();
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
        public boolean sendTypedEntitiesPatchIfCurrent(
                long expectedEpoch, UUID submitPlayerId, EntityPatchView patch) {
            Map<String, Map<String, Object>> values = new HashMap<>();
            for (int index = 0; index < patch.upsertCount(); index++) {
                Map<String, Object> data = new HashMap<>();
                int mask = patch.fieldMask(index);
                if ((mask & EntityPatchView.X) != 0) data.put("x", patch.x(index));
                if ((mask & EntityPatchView.Y) != 0) data.put("y", patch.y(index));
                if ((mask & EntityPatchView.Z) != 0) data.put("z", patch.z(index));
                if ((mask & EntityPatchView.VX) != 0) data.put("vx", patch.vx(index));
                if ((mask & EntityPatchView.VY) != 0) data.put("vy", patch.vy(index));
                if ((mask & EntityPatchView.VZ) != 0) data.put("vz", patch.vz(index));
                if ((mask & EntityPatchView.DIMENSION) != 0) data.put("dimension", patch.dimension(index));
                if ((mask & EntityPatchView.TYPE) != 0) data.put("entityType", patch.entityType(index));
                if ((mask & EntityPatchView.NAME) != 0) data.put("entityName", patch.entityName(index));
                if ((mask & EntityPatchView.WIDTH) != 0) data.put("width", patch.width(index));
                if ((mask & EntityPatchView.HEIGHT) != 0) data.put("height", patch.height(index));
                values.put(patch.upsertId(index).toString(), data);
            }
            entityReports.add(values);
            return true;
        }

        @Override
        public void sendTypedEntityKeepaliveIfNeeded(
                long expectedEpoch, UUID submitPlayerId, java.util.Collection<UUID> entityIds) {
        }

        @Override
        public void sendTabPlayersUpdate(UUID submitPlayerId, List<Map<String, Object>> tabPlayers) {
            tabReports.add(tabPlayers);
        }

        private static RuntimeGateway runtime() {
            return new RuntimeGateway() {
                @Override
                public String getCurrentDimensionId() { return "minecraft:overworld"; }
                @Override
                public UUID getLocalPlayerId() { return null; }
                @Override
                public String getClientProgramVersion() { return "test"; }
                @Override
                public String getClientProtocolVersion() { return "0.6.2"; }
                @Override
                public String getClientMinCompatibleProtocolVersion() { return "0.6.1"; }
                @Override
                public String getServerProtocolFallbackVersion() { return "0.0.0"; }
                @Override
                public String getProgramVersionUnknown() { return "unknown"; }
                @Override
                public Path getLogsDirectory() { return Path.of("build", "test-logs"); }
            };
        }

        private static TransportProcess noTransport() {
            return (uri, options, listener) -> null;
        }
    }
}
