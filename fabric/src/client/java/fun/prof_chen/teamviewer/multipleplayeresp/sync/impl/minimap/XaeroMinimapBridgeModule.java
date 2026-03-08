package fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.minimap;

import fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction.MinimapBridgeModule;
import fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction.MinimapBridgeRegistry;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.minimap.xaero.XaeroRemotePlayerProjection;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.minimap.xaero.XaeroSharedWaypointAdapter;

public final class XaeroMinimapBridgeModule implements MinimapBridgeModule {
	@Override
	public void register(MinimapBridgeRegistry registry) {
		registry.registerRemotePlayerProjection(new XaeroRemotePlayerProjection());
		registry.registerSharedWaypointAdapter(new XaeroSharedWaypointAdapter());
	}
}
