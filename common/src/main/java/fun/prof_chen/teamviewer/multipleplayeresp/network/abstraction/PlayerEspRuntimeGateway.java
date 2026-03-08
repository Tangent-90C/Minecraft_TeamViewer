package fun.prof_chen.teamviewer.multipleplayeresp.network.abstraction;

import java.util.UUID;

public interface PlayerEspRuntimeGateway {
    String getCurrentDimensionId();

    UUID getLocalPlayerId();

    String getClientProgramVersion();

    String getClientProtocolVersion();

    String getClientMinCompatibleProtocolVersion();

    String getServerProtocolFallbackVersion();

    String getProgramVersionUnknown();
}
