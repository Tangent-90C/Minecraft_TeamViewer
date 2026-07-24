package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.mapbridge.abstraction.PluginEntrypointMapBridgeModule;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.UnavailableSharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.registry.MapBridgeRegistry;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

public final class JourneyMapBridgeModule implements PluginEntrypointMapBridgeModule {
	@Override
	public String providerId() {
		return "journeymap";
	}

	@Override
	public List<String> activationModIds() {
		return List.of("journeymap");
	}

	@Override
	public String displayName() {
		return "JourneyMap";
	}

	@Override
	public void register(MapBridgeRegistry registry) {
		if (!FabricLoader.getInstance().isModLoaded("journeymap")) {
			String detail = "JourneyMap is not installed";
			registry.registerRemotePlayerProjection(new UnavailableRemotePlayerProjection(
					"journeymap-players", RemotePlayerProjection.Kind.JOURNEYMAP_MAP_MARKER,
					IntegrationSupportStatus.MOD_NOT_INSTALLED, detail));
			registry.registerRemotePlayerProjection(new UnavailableRemotePlayerProjection(
					"journeymap-player-beacons", RemotePlayerProjection.Kind.JOURNEYMAP_BEACON,
					IntegrationSupportStatus.MOD_NOT_INSTALLED, detail));
			registry.registerSharedWaypointAdapter(new UnavailableSharedWaypointMapAdapter(
					"journeymap-shared-waypoints", IntegrationSupportStatus.MOD_NOT_INSTALLED, detail));
			return;
		}
		registry.registerRemotePlayerProjection(new JourneyMapRemotePlayerProjection());
		registry.registerRemotePlayerProjection(new JourneyMapRemotePlayerBeaconProjection());
		registry.registerSharedWaypointAdapter(new JourneyMapSharedWaypointAdapter());
	}
}
