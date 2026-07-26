package fun.prof_chen.teamviewer.main_code.client.sdk;

/** Runtime support state for an optional native-mod integration. */
public enum IntegrationSupportStatus {
    AVAILABLE,
    MOD_NOT_INSTALLED,
    UNSUPPORTED_VERSION,
    NOT_IMPLEMENTED,
    ENTRYPOINT_NOT_READY,
    FAILED
}
