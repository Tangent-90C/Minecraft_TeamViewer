package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.xaero;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction.ReflectiveMapBridgeModule;
import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.registry.MapBridgeRegistry;

import java.util.List;

public final class XaeroMapBridgeModule implements ReflectiveMapBridgeModule {
	@Override
	public String providerId() {
		return "xaero";
	}

	@Override
	public List<String> activationModIds() {
		return List.of("xaerominimap", "xaeroworldmap");
	}

	@Override
	public String displayName() {
		return "Xaero";
	}

	@Override
	public void register(MapBridgeRegistry registry) {
		registry.registerRemotePlayerProjection(new XaeroWorldMapRemotePlayerProjection());
		registry.registerSharedWaypointAdapter(new XaeroMinimapSharedWaypointAdapter());
	}
}
