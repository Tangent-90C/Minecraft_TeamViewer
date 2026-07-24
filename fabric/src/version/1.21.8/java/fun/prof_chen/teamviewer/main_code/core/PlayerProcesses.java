package fun.prof_chen.teamviewer.main_code.core;

import fun.prof_chen.teamviewer.main_code.battlemap.FabricBattleMapNativeBridge;
import fun.prof_chen.teamviewer.main_code.client.ClientApplication;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricGameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterDescriptor;
import fun.prof_chen.teamviewer.main_code.client.sdk.MapAdapterBundle;
import fun.prof_chen.teamviewer.main_code.mapbridge.registry.FabricMapBridgeBootstrap;
import fun.prof_chen.teamviewer.main_code.mapbridge.registry.MapBridgeRegistry;
import fun.prof_chen.teamviewer.main_code.network.bridge.FabricRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.render.FabricHudRenderSink;
import fun.prof_chen.teamviewer.main_code.render.FabricWorldRenderSink;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minecraft 1.21.8 composition-only entrypoint. */
public final class PlayerProcesses implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("team-view-relay");
    private ClientApplication application;

    @Override
    public void onInitializeClient() {
        MapBridgeRegistry maps = FabricMapBridgeBootstrap.createRegistry();
        FabricWorldRenderSink worldSink = new FabricWorldRenderSink();
        FabricHudRenderSink hudSink = new FabricHudRenderSink();
        ClientAdapterBundle adapters = new ClientAdapterBundle(
                ClientAdapterDescriptor.complete("1.21.8"),
                new FabricRuntimeGateway(),
                new FabricGameClientBridge(),
                new FabricClientEventBridge(),
                (context, frame) -> worldSink.render((WorldRenderContext) context, frame),
                (context, frame) -> hudSink.render((DrawContext) context, frame),
                () -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new ConfigScreen(client.currentScreen));
                },
                new FabricBattleMapNativeBridge(),
                new MapAdapterBundle(maps.remotePlayerProjections(), maps.sharedWaypointAdapters()));
        application = ClientApplication.start(adapters);
        LOGGER.info("TeamViewRelay adapter {} initialized", adapters.descriptor().adapterVersion());
    }
}
