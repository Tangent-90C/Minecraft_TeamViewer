package fun.prof_chen.teamviewer.main_code.plugin;

/** One bounded, read-only status value published by a running integration plugin. */
public record PluginRuntimeState(String key, String label, String value, Long observedAtMillis) {
    public PluginRuntimeState(String key, String label, String value) {
        this(key, label, value, null);
    }

    public PluginRuntimeState {
        key = key == null ? "" : key.trim();
        label = label == null ? "" : label.trim();
        value = value == null ? "" : value.trim();
        if (observedAtMillis != null && observedAtMillis <= 0L) observedAtMillis = null;
    }
}
