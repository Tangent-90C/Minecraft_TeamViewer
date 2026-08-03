package fun.prof_chen.teamviewer.neoforge;

import fun.prof_chen.teamviewer.client.bootstrap.ClientBootstrap;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.ConfigScreenHandler;

/** Thin NeoForge entrypoint; all client orchestration is delegated to client-bootstrap. */
@Mod(TeamViewRelayNeoForge.MOD_ID)
public final class TeamViewRelayNeoForge {
    public static final String MOD_ID = "team_view_relay";

    public TeamViewRelayNeoForge(IEventBus modBus, ModContainer container) {
        NeoForgeClientContext.initialize(modBus);
        container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (ignored, parent) -> new ConfigScreen(parent)));
        ClientBootstrap.start();
    }
}
