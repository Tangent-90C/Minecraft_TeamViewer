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
        players.add(unavailableRemote("xaero-worldmap", RemotePlayerProjection.Kind.XAERO_WORLD_MAP_MARKER,
                "xaeroworldmap"));
        waypoints.add(unavailableWaypoint("xaero-minimap", "xaerominimap"));
        players.add(unavailableRemote("journeymap-players", RemotePlayerProjection.Kind.JOURNEYMAP_MAP_MARKER,
                "journeymap"));
        players.add(unavailableRemote("journeymap-player-beacons", RemotePlayerProjection.Kind.JOURNEYMAP_BEACON,
                "journeymap"));
        waypoints.add(unavailableWaypoint("journeymap-shared-waypoints", "journeymap"));
        return new MapAdapterBundle(players, waypoints);
    }

    private static RemotePlayerProjection unavailableRemote(
            String id, RemotePlayerProjection.Kind kind, String modId) {
        boolean loaded = FabricLoader.getInstance().isModLoaded(modId);
        return new UnavailableRemotePlayerProjection(id, kind,
                loaded ? IntegrationSupportStatus.ENTRYPOINT_NOT_READY : IntegrationSupportStatus.MOD_NOT_INSTALLED,
                loaded ? modId + " Lua adapter has not loaded yet" : modId + " is not installed");
    }

    private static SharedWaypointMapAdapter unavailableWaypoint(String id, String modId) {
        boolean loaded = FabricLoader.getInstance().isModLoaded(modId);
        return new UnavailableSharedWaypointMapAdapter(id,
                loaded ? IntegrationSupportStatus.ENTRYPOINT_NOT_READY : IntegrationSupportStatus.MOD_NOT_INSTALLED,
                loaded ? modId + " Lua adapter has not loaded yet" : modId + " is not installed");
    }
}
