package fun.prof_chen.teamviewer.neoforge.aio;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Java 17 outer Mod that activates exactly one isolated NeoForge adapter. */
@Mod(TeamViewRelayNeoForgeAllInOne.MOD_ID)
public final class TeamViewRelayNeoForgeAllInOne {
    public static final String MOD_ID = "team_view_relay";

    public TeamViewRelayNeoForgeAllInOne(IEventBus modBus, ModContainer container) {
        if (NeoForgeAioSelector.isClient()) {
            NeoForgeAioSelector.launchAdapter(modBus, container);
        }
    }
}
