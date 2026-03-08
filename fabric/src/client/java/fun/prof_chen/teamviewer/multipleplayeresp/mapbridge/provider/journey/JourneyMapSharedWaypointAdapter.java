package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction.SharedWaypointMapBridgeConfig;
import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.WaypointSyncGateway;

import java.util.Map;

public final class JourneyMapSharedWaypointAdapter implements SharedWaypointMapAdapter {
	@Override
	public String id() {
		return "journeymap-shared-waypoints";
	}

	@Override
	public boolean isAvailable() {
		return JourneyMapSharedWaypointBridge.isAvailable();
	}

	@Override
	public void tick(WaypointSyncGateway gateway, Map<String, SharedWaypointInfo> remoteWaypoints, boolean enabled, SharedWaypointMapBridgeConfig config) {
		JourneyMapSharedWaypointBridge.tick(gateway, remoteWaypoints, enabled, config);
	}

	@Override
	public void deleteWaypoint(String waypointId) {
		JourneyMapSharedWaypointBridge.deleteWaypoint(waypointId);
	}

	@Override
	public void clear() {
		JourneyMapSharedWaypointBridge.clear();
	}
}