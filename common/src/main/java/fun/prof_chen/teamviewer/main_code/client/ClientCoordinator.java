package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.config.Config;

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
