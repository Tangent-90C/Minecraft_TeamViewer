package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.config.Config;

/**
 * Compatibility facade for version-specific screens. It deliberately lives in common and
 * delegates to the installed control gateway instead of depending on a Fabric bootstrap.
 */
public final class PlayerProcesses {
    private PlayerProcesses() {
    }

    public static void reconnectToServer() {
        ClientServices.control().reconnect();
    }

    public static boolean isModEnable() {
        return ClientServices.control().isEnabled();
    }

    public static void setModEnable(boolean enabled) {
        ClientServices.control().setEnabled(enabled);
    }

    public static Config getConfig() {
        return ClientServices.control().getConfig();
    }

    public static NetworkManager getNetworkManager() {
        return ClientServices.control().getNetworkManager();
    }
}
