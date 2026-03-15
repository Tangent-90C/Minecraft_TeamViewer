package fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero;

import fun.prof_chen.teamviewer.main_code.mapbridge.abstraction.SharedWaypointMapBridgeConfig;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway;

import java.util.Map;

public final class XaeroMinimapSharedWaypointAdapter implements SharedWaypointMapAdapter {
	@Override
	public String id() {
		return "xaero-minimap";
	}

	@Override
	public boolean isAvailable() {
		return XaeroWaypointShareBridge.isAvailable();
	}

	@Override
	public void tick(WaypointSyncGateway gateway, Map<String, SharedWaypointInfo> remoteWaypoints, boolean enabled, SharedWaypointMapBridgeConfig config) {
		XaeroWaypointShareBridge.tick(gateway, remoteWaypoints, enabled, config);
	}

	@Override
	public void deleteWaypoint(String waypointId) {
		XaeroWaypointShareBridge.deleteSharedWaypoint(waypointId);
	}

	@Override
	public void clear() {
		XaeroWaypointShareBridge.clear();
	}
}