package fun.prof_chen.teamviewer.main_code.plugin;

/** Runtime presentation state supplied by a Lua plugin for one manifest setting. */
public record PluginSettingState(boolean visible, boolean enabled, String detail) {
    public PluginSettingState {
        detail = detail == null ? "" : detail;
    }

    public static PluginSettingState defaults() {
        return new PluginSettingState(true, true, "");
    }
}
