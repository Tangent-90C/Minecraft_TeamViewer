package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.mapbridge.abstraction.SharedWaypointMapBridgeConfig;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway;

import java.util.List;
import java.util.Map;

public interface SharedWaypointMapAdapter {
	String id();

	boolean isAvailable();

	void tick(WaypointSyncGateway gateway, Map<String, SharedWaypointInfo> remoteWaypoints, boolean enabled, SharedWaypointMapBridgeConfig config);

	void deleteWaypoint(String waypointId);

	default void deleteWaypoints(List<String> waypointIds) {
		if (waypointIds == null || waypointIds.isEmpty()) {
			return;
		}
		for (String waypointId : waypointIds) {
			deleteWaypoint(waypointId);
		}
	}

	void clear();
}