package fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.minimap.xaero;

import fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction.SharedWaypointBridgeConfig;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.SharedWaypointMinimapAdapter;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.WaypointSyncGateway;

import java.util.Map;

public final class XaeroSharedWaypointAdapter implements SharedWaypointMinimapAdapter {
	@Override
	public String id() {
		return "xaero-minimap";
	}

	@Override
	public boolean isAvailable() {
		return XaeroWaypointShareBridgeImpl.isAvailable();
	}

	@Override
	public void tick(WaypointSyncGateway gateway, Map<String, SharedWaypointInfo> remoteWaypoints, boolean enabled, SharedWaypointBridgeConfig config) {
		XaeroWaypointShareBridgeImpl.tick(gateway, remoteWaypoints, enabled, config);
	}

	@Override
	public void deleteWaypoint(String waypointId) {
		XaeroWaypointShareBridgeImpl.deleteSharedWaypoint(waypointId);
	}

	@Override
	public void clear() {
		XaeroWaypointShareBridgeImpl.clear();
	}
}