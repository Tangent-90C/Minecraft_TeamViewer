package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.mapbridge.abstraction.PluginEntrypointMapBridgeModule;
import fun.prof_chen.teamviewer.main_code.mapbridge.registry.MapBridgeRegistry;

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
		registry.registerRemotePlayerProjection(new JourneyMapRemotePlayerProjection());
		registry.registerRemotePlayerProjection(new JourneyMapRemotePlayerBeaconProjection());
		registry.registerSharedWaypointAdapter(new JourneyMapSharedWaypointAdapter());
	}
}
