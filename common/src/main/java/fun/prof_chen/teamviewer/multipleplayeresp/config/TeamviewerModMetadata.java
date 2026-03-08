package fun.prof_chen.teamviewer.multipleplayeresp.config;

/**
 * Teamviewer Mod 全局元信息。
 */
public final class TeamviewerModMetadata {
    private TeamviewerModMetadata() {
    }

    public static final String MOD_ID = "teamviewer";
    public static final String MOD_VERSION_FALLBACK = "teamviewer-mod-dev";
    public static final String PROGRAM_VERSION_UNKNOWN = "unknown";

    /**
     * 玩家 ESP 网络协议元信息（作为全局元信息的一部分）。
     */
    public static final class PlayerEspProtocol {
        private PlayerEspProtocol() {
        }

        public static final String CLIENT_PROTOCOL_VERSION = "0.4.1";
        public static final String CLIENT_MIN_COMPATIBLE_PROTOCOL_VERSION = "0.4.1";
        public static final boolean CLIENT_SUPPORTS_DELTA = true;
        public static final String SERVER_PROTOCOL_VERSION_FALLBACK = "0.0.0";
    }
}