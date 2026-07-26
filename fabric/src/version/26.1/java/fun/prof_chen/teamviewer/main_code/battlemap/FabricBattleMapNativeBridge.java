package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;

import java.util.Optional;

/** Legacy adapter-bundle slot; the SimMC implementation and reflection now live in Lua. */
public final class FabricBattleMapNativeBridge implements BattleMapNativeBridge {
    @Override public boolean isAvailable() { return false; }
    @Override public String unavailableReason() { return "SimMC Lua adapter has not loaded yet"; }
    @Override public Optional<NativeBattleMapSnapshot> capture() { return Optional.empty(); }

    @Override
    public IntegrationCapability capability() {
        return new IntegrationCapability(
                IntegrationIds.SIMMC_BATTLE_MAP, "battle-map-source",
                IntegrationSupportStatus.ENTRYPOINT_NOT_READY, unavailableReason(),
                IntegrationIds.PLUGIN_SIMMC, IntegrationImplementationSource.PLACEHOLDER,
                PluginRuntimeStatus.DISABLED);
    }
}
