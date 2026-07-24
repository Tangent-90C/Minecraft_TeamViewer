package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.config.Config;

/**
 * Version-neutral control surface used by Minecraft-specific screens.
 */
public interface ClientControlGateway {
    Config getConfig();

    NetworkManager getNetworkManager();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    void reconnect();

    default void disconnect() {
        setEnabled(false);
        getNetworkManager().disconnect();
    }
}
