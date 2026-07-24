package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.SharedWaypointRepository;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.main_code.sync.core.SharedWaypointSyncCoordinator;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Version-neutral client lifecycle and report coordinator.
 */
public final class ClientCoordinator implements ClientControlGateway {
    private static final int AUTO_CONNECT_MAX_RETRIES = 2;
    private static final long AUTO_CONNECT_RETRY_DELAY_MS = 10_000L;

    private final Config config;
    private final NetworkManager networkManager;
    private final GameClientBridge gameClient;
    private volatile boolean enabled;
    private int reportTickCounter;
    private int entityDeathTickCounter;
    private SharedWaypointRepository waypointRepository;
    private SharedWaypointSyncCoordinator waypointCoordinator;

    public ClientCoordinator(Config config, NetworkManager networkManager, GameClientBridge gameClient) {
        this.config = Objects.requireNonNull(config, "config");
        this.networkManager = Objects.requireNonNull(networkManager, "networkManager");
        this.gameClient = Objects.requireNonNull(gameClient, "gameClient");
    }

    public void onEndClientTick() {
        networkManager.pumpMainThreadTasks();
        if (!enabled) {
            return;
        }
        sendReportWhenDue();
        cancelWaypointsForDeadEntitiesWhenDue();
    }

    public void onJoinedMultiplayer() {
        if (!config.isAutoConnectOnMultiplayerJoin()) {
            return;
        }
        enabled = true;
        networkManager.disconnect();
        networkManager.connectWithReconnectLimit(AUTO_CONNECT_MAX_RETRIES, AUTO_CONNECT_RETRY_DELAY_MS);
        resetReportClock();
    }

    public void onLeftPlaySession() {
        enabled = false;
        networkManager.disconnect();
        resetReportClock();
    }

    public void resetReportClock() {
        reportTickCounter = 0;
        entityDeathTickCounter = 0;
    }

    private void cancelWaypointsForDeadEntitiesWhenDue() {
        if (!config.isAutoCancelWaypointOnEntityDeath() || waypointCoordinator == null || waypointRepository == null
                || !networkManager.isConnected() || ++entityDeathTickCounter < 10) {
            return;
        }
        entityDeathTickCounter = 0;
        ClientReportSnapshot snapshot = gameClient.captureReportSnapshot(false);
        UUID localId = snapshot.localPlayerId();
        if (localId == null) {
            return;
        }
        Map<String, SharedWaypointInfo> waypoints = waypointRepository.snapshot();
        List<String> waypointIds = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> entityIds = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, SharedWaypointInfo> entry : waypoints.entrySet()) {
            SharedWaypointInfo waypoint = entry.getValue();
            if (waypoint == null || !localId.equals(waypoint.ownerId())
                    || waypoint.targetEntityId() == null || waypoint.targetEntityId().isBlank()) {
                continue;
            }
            if (gameClient.isEntityDead(waypoint.targetEntityId())) {
                waypointIds.add(entry.getKey());
                entityIds.add(waypoint.targetEntityId());
            }
        }
        if (!entityIds.isEmpty()) {
            waypointCoordinator.cancelEntityDeath(localId, waypointIds, List.copyOf(entityIds));
        }
    }

    public void configureWaypointSupport(
            SharedWaypointRepository repository,
            SharedWaypointSyncCoordinator coordinator) {
        this.waypointRepository = Objects.requireNonNull(repository, "repository");
        this.waypointCoordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    /** Creates a platform-neutral quick mark from the target snapshot supplied by the version bridge. */
    public boolean createQuickMark() {
        if (!enabled || !networkManager.isConnected() || waypointCoordinator == null || waypointRepository == null) {
            return false;
        }
        ClientReportSnapshot client = gameClient.captureReportSnapshot(false);
        if (client.localPlayerId() == null || !client.localPlayerAlive()) {
            return false;
        }
        EntityTargetSnapshot target = gameClient.resolveMarkTarget(Math.max(16.0, config.getRenderDistance())).orElse(null);
        if (target == null || target.position() == null) {
            gameClient.showActionBar("§c[TV] 报点失败：未命中方块或实体");
            return false;
        }

        UUID ownerId = client.localPlayerId();
        String ownerName = client.players().stream()
                .filter(player -> ownerId.equals(player.id()))
                .map(PlayerSnapshot::name)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(ownerId.toString());
        int x = (int) Math.floor(target.position().x());
        int y = (int) Math.floor(target.position().y());
        int z = (int) Math.floor(target.position().z());
        long createdAt = System.currentTimeMillis();
        String idSeed = ownerId + "|" + createdAt + "|" + client.dimension() + "|" + x + "|" + y + "|" + z;
        String waypointId = UUID.nameUUIDFromBytes(idSeed.getBytes(StandardCharsets.UTF_8)).toString();
        boolean entityTarget = target.entityId() != null && !target.entityId().isBlank();
        String displayName = entityTarget && target.entityName() != null && !target.entityName().isBlank()
                ? target.entityName() : null;
        SharedWaypointInfo waypoint = new SharedWaypointInfo(
                waypointId, ownerId, ownerName,
                entityTarget ? "报点[实体] " + displayName : "报点", "!",
                x, y, z, client.dimension(), 0xFF8C00, createdAt,
                entityTarget ? "entity" : "block", target.entityId(), target.entityType(), target.entityName(),
                "quick", null, null);

        int maxCount = config.getMaxQuickMarkCount();
        List<String> overflow = waypointRepository.snapshot().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> ownerId.equals(entry.getValue().ownerId()))
                .filter(entry -> "quick".equalsIgnoreCase(entry.getValue().waypointKind()))
                .sorted(Comparator.comparingLong(entry -> entry.getValue().createdAt()))
                .limit(Math.max(0, waypointRepository.snapshot().values().stream()
                        .filter(value -> value != null && ownerId.equals(value.ownerId())
                                && "quick".equalsIgnoreCase(value.waypointKind())).count() - maxCount + 1))
                .map(Map.Entry::getKey)
                .toList();
        if (!overflow.isEmpty()) {
            waypointCoordinator.deleteLocalWaypoints(ownerId, overflow);
        }
        waypointCoordinator.upsertLocalWaypoints(ownerId, Map.of(
                waypointId, WaypointSyncPayload.quickMark(
                        waypoint, config.getWaypointTimeoutSeconds(), maxCount)));
        gameClient.showActionBar(entityTarget
                ? "§6[TV] 已报点实体: " + displayName + " @ " + x + " " + y + " " + z
                : "§6[TV] 已报点方块: " + x + " " + y + " " + z);
        return true;
    }

    private void sendReportWhenDue() {
        reportTickCounter++;
        int targetInterval = networkManager.isConnected()
                ? networkManager.getNegotiatedReportIntervalTicks()
                : config.getUpdateInterval();
        if (!networkManager.isConnected() || reportTickCounter < Math.max(1, targetInterval)) {
            return;
        }
        reportTickCounter = 0;

        ClientReportSnapshot snapshot = gameClient.captureReportSnapshot(config.isUploadEntities());
        if (snapshot.localPlayerId() == null || !snapshot.localPlayerAlive()) {
            return;
        }

        UUID submitPlayerId = snapshot.localPlayerId();
        List<Map<String, Object>> tabPlayers = snapshot.tabPlayers().stream()
                .map(TabPlayerSnapshot::toProtocolMap)
                .toList();
        Map<UUID, Map<String, Object>> players = new LinkedHashMap<>();
        for (PlayerSnapshot player : snapshot.players()) {
            players.put(player.id(), player.toProtocolMap());
        }
        networkManager.sendTabPlayersUpdate(submitPlayerId, tabPlayers);
        networkManager.sendPlayersUpdate(submitPlayerId, players);

        if (config.isUploadEntities()) {
            Map<String, Map<String, Object>> entities = new LinkedHashMap<>();
            for (EntitySnapshot entity : snapshot.entities()) {
                entities.put(entity.id(), entity.toProtocolMap());
            }
            networkManager.sendEntitiesUpdate(submitPlayerId, entities);
        }
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        resetReportClock();
        if (enabled) {
            networkManager.connect();
        } else {
            networkManager.disconnect();
        }
    }

    @Override
    public void reconnect() {
        networkManager.disconnect();
        if (enabled) {
            networkManager.connect();
        }
        resetReportClock();
    }
}
