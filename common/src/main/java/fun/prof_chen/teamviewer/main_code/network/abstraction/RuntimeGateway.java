package fun.prof_chen.teamviewer.main_code.network.abstraction;

import java.nio.file.Path;
import java.util.UUID;

public interface RuntimeGateway {
    String getCurrentDimensionId();

    UUID getLocalPlayerId();

    String getClientProgramVersion();

    String getClientProtocolVersion();

    String getClientMinCompatibleProtocolVersion();

    String getServerProtocolFallbackVersion();

    String getProgramVersionUnknown();

    Path getLogsDirectory();
}
