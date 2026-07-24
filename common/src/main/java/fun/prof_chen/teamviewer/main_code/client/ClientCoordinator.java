package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.sync.api.SharedWaypointRepository;
import fun.prof_chen.teamviewer.main_code.sync.api.RemotePlayerRepository;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.main_code.sync.core.SharedWaypointSyncCoordinator;
import fun.prof_chen.teamviewer.main_code.sync.core.RemotePlayerProjectionCoordinator;
import fun.prof_chen.teamviewer.main_code.renderbridge.core.WorldRenderPlanner;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import fun.prof_chen.teamviewer.main_code.hud.core.HudPlanner;
import fun.prof_chen.teamviewer.main_code.hud.model.HudFrame;
import fun.prof_chen.teamviewer.main_code.hud.model.LocalMarkedState;
import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapCoordinator;
import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapNativeBridge;

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
    private static final long MARK_DOUBLE_CLICK_MS = 300L;
    private static final double MARK_CANCEL_BASE_RADIUS = 1.2D;
    private static final double MARK_CANCEL_RADIUS_PER_BLOCK = 0.02D;
    private static final double MARK_CANCEL_MAX_RADIUS = 4.0D;

    private final Config config;
    private final NetworkManager networkManager;
    private final GameClientBridge gameClient;
    private volatile boolean enabled;
    private int reportTickCounter;
    private int entityDeathTickCounter;
    private SharedWaypointRepository waypointRepository;
    private SharedWaypointSyncCoordinator waypointCoordinator;
    private RemotePlayerRepository remotePlayerRepository;
    private RemotePlayerProjectionCoordinator remotePlayerProjectionCoordinator;
    private WorldRenderPlanner worldRenderPlanner;
    private final HudPlanner hudPlanner = new HudPlanner();
    private boolean middlePressedLastTick;
    private long lastMiddleClickAt;
    private LocalMarkedState localMarkedState = LocalMarkedState.inactive();
    private String lastMarkedFingerprint = "";
    private BattleMapCoordinator battleMapCoordinator;

    public ClientCoordinator(Config config, NetworkManager networkManager, GameClientBridge gameClient) {
        this.config = Objects.requireNonNull(config, "config");
        this.networkManager = Objects.requireNonNull(networkManager, "networkManager");
        this.gameClient = Objects.requireNonNull(gameClient, "gameClient");
    }

    public void onEndClientTick() {
        networkManager.pumpMainThreadTasks();
        if (battleMapCoordinator != null) {
            battleMapCoordinator.tick(enabled);
        }
        if (!enabled) {
            return;
        }
        sendReportWhenDue();
        cancelWaypointsForDeadEntitiesWhenDue();
        handleMiddleMouseDoubleClick();
        updateLocalMarkedState();
        if (remotePlayerProjectionCoordinator != null && remotePlayerRepository != null) {
            remotePlayerProjectionCoordinator.tick(remotePlayerRepository.snapshot(), enabled);
        }
        if (waypointCoordinator != null) {
            waypointCoordinator.tick(enabled);
        }
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
        clearRuntimeState();
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

    public void configureRuntimeSupport(
            RemotePlayerRepository remotePlayerRepository,
            SharedWaypointRepository sharedWaypointRepository,
            SharedWaypointSyncCoordinator sharedWaypointSyncCoordinator,
            fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway waypointGateway,
            RemotePlayerProjectionCoordinator remotePlayerProjectionCoordinator) {
        this.remotePlayerRepository = Objects.requireNonNull(remotePlayerRepository, "remotePlayerRepository");
        this.remotePlayerProjectionCoordinator = Objects.requireNonNull(remotePlayerProjectionCoordinator, "remotePlayerProjectionCoordinator");
        configureWaypointSupport(sharedWaypointRepository, sharedWaypointSyncCoordinator);
        this.worldRenderPlanner = new WorldRenderPlanner(config, networkManager::getPlayerMarkTeam, waypointGateway);
    }

    /** Installs the native optional-mod port; NodeMC scoreboard parsing remains entirely in common. */
    public void configureBattleMapSupport(BattleMapNativeBridge nativeBridge) {
        if (battleMapCoordinator != null) {
            throw new IllegalStateException("Battle-map support is already configured");
        }
        battleMapCoordinator = new BattleMapCoordinator(config, networkManager, gameClient,
                Objects.requireNonNull(nativeBridge, "nativeBridge"));
        networkManager.addConnectionStatusListener(connected -> battleMapCoordinator.markPending());
    }

    public boolean handleQuickMarkAction(boolean tryCancelFirst) {
        if (tryCancelFirst && tryCancelTargetedWaypoint()) {
            return true;
        }
        return createQuickMark();
    }

    public WorldRenderFrame buildWorldRenderFrame() {
        if (worldRenderPlanner == null || remotePlayerRepository == null || waypointRepository == null) {
            return WorldRenderFrame.empty();
        }
        return worldRenderPlanner.plan(enabled, gameClient.captureWorldSnapshot(),
                remotePlayerRepository.snapshot(), waypointRepository.snapshot());
    }

    public HudFrame buildHudFrame() {
        return hudPlanner.plan(config, networkManager, enabled, localMarkedState);
    }

    public LocalMarkedState getLocalMarkedState() {
        return localMarkedState;
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

    private void handleMiddleMouseDoubleClick() {
        boolean pressed = gameClient.isGameplayInputAvailable() && gameClient.isMiddleMouseButtonDown();
        if (!config.isEnableMiddleDoubleClickMark()) {
            middlePressedLastTick = pressed;
            lastMiddleClickAt = 0L;
            return;
        }
        if (pressed && !middlePressedLastTick) {
            long now = System.currentTimeMillis();
            if (lastMiddleClickAt > 0L && now - lastMiddleClickAt <= MARK_DOUBLE_CLICK_MS) {
                handleQuickMarkAction(true);
                lastMiddleClickAt = 0L;
            } else {
                lastMiddleClickAt = now;
            }
        }
        middlePressedLastTick = pressed;
    }

    private boolean tryCancelTargetedWaypoint() {
        if (!enabled || !config.isEnableMiddleClickCancelWaypoint() || waypointRepository == null || waypointCoordinator == null
                || !networkManager.isConnected()) {
            return false;
        }
        ClientWorldSnapshot world = gameClient.captureWorldSnapshot();
        if (!world.available()) {
            return false;
        }
        EntityTargetSnapshot target = gameClient.resolveMarkTarget(Math.max(16.0, config.getRenderDistance())).orElse(null);
        if (target == null || target.position() == null) {
            return false;
        }
        double targetDistance = distance(world.localPlayerPosition(), target.position());
        double radius = Math.min(MARK_CANCEL_MAX_RADIUS, MARK_CANCEL_BASE_RADIUS + targetDistance * MARK_CANCEL_RADIUS_PER_BLOCK);
        String nearestId = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, SharedWaypointInfo> entry : waypointRepository.snapshot().entrySet()) {
            SharedWaypointInfo waypoint = entry.getValue();
            if (waypoint == null || !world.localPlayerId().equals(waypoint.ownerId())
                    || (waypoint.dimension() != null && !waypoint.dimension().isBlank() && !waypoint.dimension().equals(world.dimension()))) {
                continue;
            }
            if (target.entityId() != null && target.entityId().equals(waypoint.targetEntityId())) {
                nearestId = entry.getKey();
                break;
            }
            Position3D position = new Position3D(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5);
            double currentDistance = distance(position, target.position());
            if (currentDistance <= radius && currentDistance < nearestDistance) {
                nearestDistance = currentDistance;
                nearestId = entry.getKey();
            }
        }
        if (nearestId == null) {
            return false;
        }
        waypointCoordinator.deleteLocalWaypoints(world.localPlayerId(), List.of(nearestId));
        gameClient.showActionBar("§a[TV] 已撤销报点");
        return true;
    }

    private void updateLocalMarkedState() {
        if (waypointRepository == null) {
            localMarkedState = LocalMarkedState.inactive();
            return;
        }
        ClientWorldSnapshot world = gameClient.captureWorldSnapshot();
        if (!world.available()) {
            localMarkedState = LocalMarkedState.inactive();
            lastMarkedFingerprint = "";
            return;
        }
        java.util.LinkedHashSet<String> owners = new java.util.LinkedHashSet<>();
        int count = 0;
        for (SharedWaypointInfo waypoint : waypointRepository.snapshot().values()) {
            if (waypoint == null || !"entity".equalsIgnoreCase(waypoint.targetType())
                    || (waypoint.dimension() != null && !waypoint.dimension().isBlank() && !waypoint.dimension().equals(world.dimension()))) {
                continue;
            }
            boolean targetsLocal = world.localPlayerId().toString().equals(waypoint.targetEntityId())
                    || (world.localPlayerName() != null && world.localPlayerName().equalsIgnoreCase(waypoint.targetEntityName()));
            if (!targetsLocal) {
                continue;
            }
            count++;
            if (waypoint.ownerName() != null && !waypoint.ownerName().isBlank()) {
                owners.add(waypoint.ownerName().trim());
            }
        }
        if (count == 0) {
            localMarkedState = LocalMarkedState.inactive();
            lastMarkedFingerprint = "";
            return;
        }
        String summary = owners.isEmpty() ? "队友" : owners.iterator().next();
        if (owners.size() > 1) {
            summary += " 等" + owners.size() + " 人";
        }
        localMarkedState = new LocalMarkedState(true, count, summary);
        String fingerprint = count + "|" + summary;
        if (!fingerprint.equals(lastMarkedFingerprint)) {
            gameClient.showActionBar("§e[TV] 你已被标记，来源: " + summary);
            lastMarkedFingerprint = fingerprint;
        }
    }

    private static double distance(Position3D first, Position3D second) {
        if (first == null || second == null) {
            return Double.MAX_VALUE;
        }
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        double z = first.z() - second.z();
        return Math.sqrt(x * x + y * y + z * z);
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
            clearRuntimeState();
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

    @Override
    public void showActionBar(String message) {
        gameClient.showActionBar(message);
    }

    private void clearRuntimeState() {
        if (remotePlayerRepository != null) remotePlayerRepository.clear();
        if (waypointRepository != null) waypointRepository.clear();
        if (remotePlayerProjectionCoordinator != null) remotePlayerProjectionCoordinator.clear();
        if (waypointCoordinator != null) waypointCoordinator.clear();
        if (worldRenderPlanner != null) worldRenderPlanner.clear();
        if (battleMapCoordinator != null) battleMapCoordinator.reset();
        localMarkedState = LocalMarkedState.inactive();
        lastMarkedFingerprint = "";
        middlePressedLastTick = false;
        lastMiddleClickAt = 0L;
    }
}
