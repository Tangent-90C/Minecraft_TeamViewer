package fun.prof_chen.teamviewer.main_code.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * TeamViewRelay Mod 全局元信息。
 */
public final class TeamviewerModMetadata {
    private static final Properties BUILD_PROPERTIES = loadBuildProperties();

    private TeamviewerModMetadata() {
    }

    public static final String MOD_ID = "teamviewer";
    public static final String MOD_VERSION_FALLBACK = "team-view-relay-mod-dev";
    public static final String PROGRAM_VERSION_UNKNOWN = "unknown";

    private static Properties loadBuildProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = TeamviewerModMetadata.class.getClassLoader()
                .getResourceAsStream("teamviewer-build.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
        }
        return properties;
    }

    private static String getBuildProperty(String key, String fallback) {
        return BUILD_PROPERTIES.getProperty(key, fallback);
    }

    /**
     * 玩家 渲染 网络协议元信息（作为全局元信息的一部分）。
     */
    public static final class MetaProtocol {
        private MetaProtocol() {
        }

        public static final String CLIENT_PROTOCOL_VERSION = getBuildProperty("network_protocol_version", "0.6.0");
        public static final String CLIENT_MIN_COMPATIBLE_PROTOCOL_VERSION = getBuildProperty("network_min_compatible_protocol_version", "0.6.0");
        public static final boolean CLIENT_SUPPORTS_DELTA = true;
        public static final String SERVER_PROTOCOL_VERSION_FALLBACK = "0.0.0";
    }
}
