package fun.prof_chen.teamviewer.main_code.network.abstraction;

import java.nio.file.Path;
import java.util.UUID;

public interface RuntimeGateway {
    String getCurrentDimensionId();

    UUID getLocalPlayerId();

    String getClientProgramVersion();

    /** Exact Minecraft runtime version when supplied by the platform loader. */
    default String getMinecraftVersion() {
        return "unknown";
    }

    String getClientProtocolVersion();

    String getClientMinCompatibleProtocolVersion();

    String getServerProtocolFallbackVersion();

    String getProgramVersionUnknown();

    Path getLogsDirectory();

    /** Fabric config directory. The common runtime owns config file naming, parsing and saving. */
    default Path getConfigDirectory() {
        Path logs = getLogsDirectory();
        return logs == null || logs.getParent() == null ? Path.of("config") : logs.getParent().resolve("config");
    }
}
