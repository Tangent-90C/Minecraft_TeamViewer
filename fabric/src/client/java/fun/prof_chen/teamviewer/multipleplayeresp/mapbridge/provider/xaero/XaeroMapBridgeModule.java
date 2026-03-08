package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.xaero;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction.MapBridgeModule;
import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.registry.MapBridgeRegistry;

public final class XaeroMapBridgeModule implements MapBridgeModule {
	@Override
	public void register(MapBridgeRegistry registry) {
		registry.registerRemotePlayerProjection(new XaeroWorldMapRemotePlayerProjection());
		registry.registerSharedWaypointAdapter(new XaeroMinimapSharedWaypointAdapter());
	}
}
