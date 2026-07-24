package fun.prof_chen.teamviewer.main_code.core;

import fun.prof_chen.teamviewer.main_code.battlemap.FabricBattleMapNativeBridge;
import fun.prof_chen.teamviewer.main_code.client.ClientApplication;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricGameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterDescriptor;
import fun.prof_chen.teamviewer.main_code.client.sdk.MapAdapterBundle;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapRemotePlayerBeaconProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapSharedWaypointAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero.XaeroMinimapSharedWaypointAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero.XaeroWorldMapRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.network.bridge.FabricRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.render.FabricHudRenderSink;
import fun.prof_chen.teamviewer.main_code.render.FabricWorldRenderSink;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Minecraft 26.1 composition-only entrypoint. */
public final class PlayerProcesses implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("team-view-relay");
    private ClientApplication application;

    @Override
    public void onInitializeClient() {
        FabricWorldRenderSink worldSink = new FabricWorldRenderSink();
        FabricHudRenderSink hudSink = new FabricHudRenderSink();
        ClientAdapterBundle adapters = new ClientAdapterBundle(
                ClientAdapterDescriptor.complete("26.1"),
                new FabricRuntimeGateway(),
                new FabricGameClientBridge(),
                new FabricClientEventBridge(),
                (context, frame) -> worldSink.render(null, frame),
                (context, frame) -> hudSink.render((GuiGraphicsExtractor) context, frame),
                () -> {
                    Minecraft client = Minecraft.getInstance();
                    client.setScreen(new ConfigScreen(client.screen));
                },
                new FabricBattleMapNativeBridge(),
                new MapAdapterBundle(
                        List.of(new XaeroWorldMapRemotePlayerProjection(),
                                new JourneyMapRemotePlayerProjection(),
                                new JourneyMapRemotePlayerBeaconProjection()),
                        List.of(new JourneyMapSharedWaypointAdapter(),
                                new XaeroMinimapSharedWaypointAdapter())));
        application = ClientApplication.start(adapters);
        LOGGER.info("TeamViewRelay adapter {} initialized", adapters.descriptor().adapterVersion());
    }
}
