package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.main_code.client.bridge.ClientControlGateway;

import java.util.Objects;

/**
 * Loader bootstrap installs one control gateway before a version-specific UI is opened.
 */
public final class ClientServices {
    private static volatile ClientControlGateway controlGateway;

    private ClientServices() {
    }

    public static void install(ClientControlGateway gateway) {
        controlGateway = Objects.requireNonNull(gateway, "gateway");
    }

    public static ClientControlGateway control() {
        ClientControlGateway current = controlGateway;
        if (current == null) {
            throw new IllegalStateException("TeamViewRelay client services have not been initialized");
        }
        return current;
    }

    public static void clear(ClientControlGateway gateway) {
        if (controlGateway == gateway) {
            controlGateway = null;
        }
    }
}
