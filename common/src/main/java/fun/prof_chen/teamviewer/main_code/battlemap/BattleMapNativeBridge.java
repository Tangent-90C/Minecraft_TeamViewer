package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;

import java.util.Optional;

public interface BattleMapNativeBridge {
    boolean isAvailable();
    String unavailableReason();
    Optional<NativeBattleMapSnapshot> capture();

    /** Explicit optional-integration state used by capability reports. */
    default IntegrationCapability capability() {
        String reason = unavailableReason();
        boolean available = isAvailable();
        IntegrationSupportStatus status;
        if (available) {
            status = IntegrationSupportStatus.AVAILABLE;
        } else if (reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("not_loaded")) {
            status = IntegrationSupportStatus.MOD_NOT_INSTALLED;
        } else {
            status = IntegrationSupportStatus.FAILED;
        }
        return new IntegrationCapability(
                "simmc-native-battle-map",
                "battle-map-source",
                status,
                reason == null ? "" : reason,
                fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds.PLUGIN_SIMMC,
                available
                        ? fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource.JAVA_NATIVE
                        : fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource.PLACEHOLDER,
                fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus.DISABLED);
    }
}
