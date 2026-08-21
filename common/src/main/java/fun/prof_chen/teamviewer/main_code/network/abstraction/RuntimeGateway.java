package fun.prof_chen.teamviewer.main_code.network.abstraction;

import fun.prof_chen.teamviewer.main_code.config.TeamviewerModMetadata;

import java.nio.file.Path;
import java.awt.Desktop;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface RuntimeGateway {
    String getCurrentDimensionId();

    UUID getLocalPlayerId();

    String getClientProgramVersion();

    /** Exact Minecraft runtime version when supplied by the platform loader. */
    default String getMinecraftVersion() {
        return "unknown";
    }

    default String getLoaderId() {
        return "unknown";
    }

    default boolean isModLoaded(String modId) {
        return false;
    }

    default String getModVersion(String modId) { return "unknown"; }

    default Object getPluginService(String serviceId) { return null; }

    /**
     * Copy trusted local UI text through the native Minecraft clipboard implementation.
     * Calls are made from the client UI thread; external adapters may leave this unsupported.
     */
    default boolean copyTextToClipboard(String text) {
        return false;
    }

    /** Resolve a trusted plugin class through the loader that owns the concrete platform adapter. */
    default Class<?> resolvePluginClass(String binaryName) throws ClassNotFoundException {
        ClassLoader platformLoader = getClass().getClassLoader();
        try {
            return Class.forName(binaryName, false, platformLoader);
        } catch (ClassNotFoundException first) {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader != null && contextLoader != platformLoader) {
                try {
                    return Class.forName(binaryName, false, contextLoader);
                } catch (ClassNotFoundException contextFailure) {
                    first.addSuppressed(contextFailure);
                }
            }
            throw first;
        }
    }

    default String getClientProtocolVersion() {
        return TeamviewerModMetadata.MetaProtocol.CLIENT_PROTOCOL_VERSION;
    }

    default String getClientMinCompatibleProtocolVersion() {
        return TeamviewerModMetadata.MetaProtocol.CLIENT_MIN_COMPATIBLE_PROTOCOL_VERSION;
    }

    default String getServerProtocolFallbackVersion() {
        return TeamviewerModMetadata.MetaProtocol.SERVER_PROTOCOL_VERSION_FALLBACK;
    }

    default String getProgramVersionUnknown() {
        return TeamviewerModMetadata.PROGRAM_VERSION_UNKNOWN;
    }

    Path getLogsDirectory();

    /** Loader config directory. The common runtime owns config file naming, parsing and saving. */
    default Path getConfigDirectory() {
        Path logs = getLogsDirectory();
        return logs == null || logs.getParent() == null ? Path.of("config") : logs.getParent().resolve("config");
    }

    /** Open a trusted configuration directory with the host desktop file manager. */
    default boolean openDirectory(Path directory) {
        if (directory == null || !java.nio.file.Files.isDirectory(directory)) return false;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile());
                return true;
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            List<String> command = os.contains("win")
                    ? List.of("explorer.exe", directory.toAbsolutePath().toString())
                    : os.contains("mac")
                    ? List.of("open", directory.toAbsolutePath().toString())
                    : List.of("xdg-open", directory.toAbsolutePath().toString());
            new ProcessBuilder(command).start();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
