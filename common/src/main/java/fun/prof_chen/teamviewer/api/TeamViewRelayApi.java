package fun.prof_chen.teamviewer.api;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.ClientServices;

import java.util.List;

/**
 * Stable, read-only entry point for optional integrations that consume
 * TeamViewRelay's remote-player state.
 */
public final class TeamViewRelayApi {
    public static final int API_VERSION = 1;

    private TeamViewRelayApi() {
    }

    /**
     * Returns the latest immutable remote-player snapshot. This method is
     * fail-closed during startup, shutdown, and disconnected states.
     */
    public static RemotePlayerBatch remotePlayers() {
        try {
            var control = ClientServices.control();
            NetworkManager network = control.getNetworkManager();
            if (network == null || !network.isConnected()) {
                return disconnected();
            }
            List<RemotePlayerSnapshot> players = network.getRemotePlayerSnapshots().stream()
                    .map(player -> {
                        var relation = control.resolvePlayerRelation(player.uuid());
                        if (relation == null || !relation.resolved()) return player;
                        return new RemotePlayerSnapshot(
                                player.uuid(), player.name(), player.dimension(),
                                player.x(), player.y(), player.z(),
                                player.velocityX(), player.velocityY(), player.velocityZ(),
                                player.health(), player.maxHealth(), player.armor(), player.riding(),
                                player.width(), player.height(), relation.relation());
                    })
                    .toList();
            return new RemotePlayerBatch(API_VERSION, true, players);
        } catch (RuntimeException | LinkageError ignored) {
            return disconnected();
        }
    }

    /** Returns remote players with effective relation origin and interaction hint. */
    public static RemotePlayerViewBatch remotePlayerViews() {
        try {
            var control = ClientServices.control();
            NetworkManager network = control.getNetworkManager();
            if (network == null || !network.isConnected()) {
                return new RemotePlayerViewBatch(API_VERSION, false, List.of());
            }
            List<RemotePlayerView> players = network.getRemotePlayerSnapshots().stream()
                    .map(player -> new RemotePlayerView(player, control.playerInteraction(player.uuid())))
                    .toList();
            return new RemotePlayerViewBatch(API_VERSION, true, players);
        } catch (RuntimeException | LinkageError ignored) {
            return new RemotePlayerViewBatch(API_VERSION, false, List.of());
        }
    }

    public static PlayerInteractionState playerInteraction(java.util.UUID playerId) {
        try {
            return ClientServices.control().playerInteraction(playerId);
        } catch (RuntimeException | LinkageError ignored) {
            return PlayerInteractionState.unresolved();
        }
    }

    private static RemotePlayerBatch disconnected() {
        return new RemotePlayerBatch(API_VERSION, false, List.of());
    }
}
