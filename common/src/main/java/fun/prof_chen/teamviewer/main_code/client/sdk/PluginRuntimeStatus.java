package fun.prof_chen.teamviewer.main_code.client.sdk;

/** Runtime activation state, intentionally separate from native capability support. */
public enum PluginRuntimeStatus {
    ACTIVE,
    DISABLED,
    PENDING_RESTART,
    INCOMPATIBLE,
    LOAD_FAILED,
    SUSPENDED
}
