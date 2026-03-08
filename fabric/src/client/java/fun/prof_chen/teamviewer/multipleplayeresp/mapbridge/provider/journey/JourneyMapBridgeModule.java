package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction.MapBridgeModule;
import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.registry.MapBridgeRegistry;

public final class JourneyMapBridgeModule implements MapBridgeModule {
	@Override
	public void register(MapBridgeRegistry registry) {
		registry.registerRemotePlayerProjection(new JourneyMapRemotePlayerProjection());
		registry.registerSharedWaypointAdapter(new JourneyMapSharedWaypointAdapter());
	}
}