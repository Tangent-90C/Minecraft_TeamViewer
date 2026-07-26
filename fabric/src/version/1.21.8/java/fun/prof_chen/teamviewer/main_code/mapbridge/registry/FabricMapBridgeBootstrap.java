package fun.prof_chen.teamviewer.main_code.mapbridge.registry;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableSharedWaypointMapAdapter;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricMapBridgeBootstrap {
	private static final Logger LOGGER = LoggerFactory.getLogger(FabricMapBridgeBootstrap.class);

	private FabricMapBridgeBootstrap() {
	}

	public static MapBridgeRegistry createRegistry() {
		MapBridgeRegistry registry = new MapBridgeRegistry();
		registerRemote(registry, "xaero-worldmap", RemotePlayerProjection.Kind.XAERO_WORLD_MAP_MARKER,
				"xaeroworldmap", "Xaero World Map Lua adapter has not loaded yet");
		registerWaypoint(registry, "xaero-minimap", "xaerominimap",
				"Xaero Minimap Lua adapter has not loaded yet");
		registerRemote(registry, "journeymap-players", RemotePlayerProjection.Kind.JOURNEYMAP_MAP_MARKER,
				"journeymap", "JourneyMap Lua adapter has not loaded yet");
		registerRemote(registry, "journeymap-player-beacons", RemotePlayerProjection.Kind.JOURNEYMAP_BEACON,
				"journeymap", "JourneyMap Lua adapter has not loaded yet");
		registerWaypoint(registry, "journeymap-shared-waypoints", "journeymap",
				"JourneyMap Lua adapter has not loaded yet");
		return registry;
	}

	private static void registerRemote(MapBridgeRegistry registry, String id,
			RemotePlayerProjection.Kind kind, String modId, String pendingDetail) {
		boolean loaded = FabricLoader.getInstance().isModLoaded(modId);
		registry.registerRemotePlayerProjection(new UnavailableRemotePlayerProjection(id, kind,
				loaded ? IntegrationSupportStatus.ENTRYPOINT_NOT_READY : IntegrationSupportStatus.MOD_NOT_INSTALLED,
				loaded ? pendingDetail : modId + " is not installed"));
	}

	private static void registerWaypoint(
			MapBridgeRegistry registry, String id, String modId, String pendingDetail) {
		boolean loaded = FabricLoader.getInstance().isModLoaded(modId);
		registry.registerSharedWaypointAdapter(new UnavailableSharedWaypointMapAdapter(id,
				loaded ? IntegrationSupportStatus.ENTRYPOINT_NOT_READY : IntegrationSupportStatus.MOD_NOT_INSTALLED,
				loaded ? pendingDetail : modId + " is not installed"));
	}
}
