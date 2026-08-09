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
            NetworkManager network = ClientServices.control().getNetworkManager();
            if (network == null || !network.isConnected()) {
                return disconnected();
            }
            return new RemotePlayerBatch(API_VERSION, true, network.getRemotePlayerSnapshots());
        } catch (RuntimeException | LinkageError ignored) {
            return disconnected();
        }
    }

    private static RemotePlayerBatch disconnected() {
        return new RemotePlayerBatch(API_VERSION, false, List.of());
    }
}
