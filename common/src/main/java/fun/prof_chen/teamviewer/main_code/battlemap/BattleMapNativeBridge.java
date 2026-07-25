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
        return new IntegrationCapability(
                "simmc-native-battle-map",
                "battle-map-native",
                isAvailable() ? IntegrationSupportStatus.AVAILABLE : IntegrationSupportStatus.MOD_NOT_INSTALLED,
                reason == null ? "" : reason);
    }
}
