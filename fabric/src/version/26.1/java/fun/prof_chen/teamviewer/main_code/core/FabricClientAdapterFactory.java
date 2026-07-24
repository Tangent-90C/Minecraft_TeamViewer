package fun.prof_chen.teamviewer.main_code.core;

import fun.prof_chen.teamviewer.main_code.battlemap.FabricBattleMapNativeBridge;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricGameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.MapAdapterBundle;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableSharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapRemotePlayerBeaconProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapSharedWaypointAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero.XaeroMinimapSharedWaypointAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero.XaeroWorldMapRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.network.bridge.FabricRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.render.FabricHudRenderSink;
import fun.prof_chen.teamviewer.main_code.render.FabricWorldRenderSink;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Minecraft 26.1 native adapter factory. */
public final class FabricClientAdapterFactory implements ClientAdapterFactory<LevelRenderContext, GuiGraphicsExtractor> {
    @Override
    public ClientAdapterBundle<LevelRenderContext, GuiGraphicsExtractor> create() {
        return new ClientAdapterBundle<>(
                "26.1",
                new FabricRuntimeGateway(),
                new FabricGameClientBridge(),
                new FabricClientEventBridge(),
                new FabricWorldRenderSink(),
                new FabricHudRenderSink(),
                controller -> {
                    Minecraft client = Minecraft.getInstance();
                    client.setScreen(new ConfigScreen(client.screen, controller));
                },
                new FabricBattleMapNativeBridge(),
                createMapAdapters());
    }

    private static MapAdapterBundle createMapAdapters() {
        List<RemotePlayerProjection> players = new ArrayList<>();
        List<SharedWaypointMapAdapter> waypoints = new ArrayList<>();
        players.add(new XaeroWorldMapRemotePlayerProjection());
        waypoints.add(new XaeroMinimapSharedWaypointAdapter());
        if (FabricLoader.getInstance().isModLoaded("journeymap")) {
            players.add(new JourneyMapRemotePlayerProjection());
            players.add(new JourneyMapRemotePlayerBeaconProjection());
            waypoints.add(new JourneyMapSharedWaypointAdapter());
        } else {
            String detail = "JourneyMap is not installed";
            players.add(new UnavailableRemotePlayerProjection(
                    "journeymap-players", RemotePlayerProjection.Kind.JOURNEYMAP_MAP_MARKER,
                    IntegrationSupportStatus.MOD_NOT_INSTALLED, detail));
            players.add(new UnavailableRemotePlayerProjection(
                    "journeymap-player-beacons", RemotePlayerProjection.Kind.JOURNEYMAP_BEACON,
                    IntegrationSupportStatus.MOD_NOT_INSTALLED, detail));
            waypoints.add(new UnavailableSharedWaypointMapAdapter(
                    "journeymap-shared-waypoints", IntegrationSupportStatus.MOD_NOT_INSTALLED, detail));
        }
        return new MapAdapterBundle(players, waypoints);
    }
}
