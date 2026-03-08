package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction.PluginEntrypointMapBridgeModule;
import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.registry.MapBridgeRegistry;

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
		registry.registerSharedWaypointAdapter(new JourneyMapSharedWaypointAdapter());
	}
}