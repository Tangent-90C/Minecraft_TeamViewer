package fun.prof_chen.teamviewer.neoforge.adapter.battlemap;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapNativeBridge;
import fun.prof_chen.teamviewer.main_code.battlemap.NativeBattleMapSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;

import java.util.Optional;

/** Explicit first-release SimMC placeholder; the port is present and never silently omitted. */
public final class NeoForgeUnsupportedBattleMapBridge implements BattleMapNativeBridge {
    private static final String DETAIL = "SimMC native integration is not supported by the NeoForge adapter yet";

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String unavailableReason() {
        return "unsupported_neoforge_version";
    }

    @Override
    public Optional<NativeBattleMapSnapshot> capture() {
        return Optional.empty();
    }

    @Override
    public IntegrationCapability capability() {
        return new IntegrationCapability("simmc-native-battle-map", "battle-map-native",
                IntegrationSupportStatus.UNSUPPORTED_VERSION, DETAIL);
    }
}
