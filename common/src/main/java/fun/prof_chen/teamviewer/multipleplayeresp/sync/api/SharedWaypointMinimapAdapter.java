package fun.prof_chen.teamviewer.multipleplayeresp.sync.api;

import fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction.SharedWaypointBridgeConfig;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;

import java.util.List;
import java.util.Map;

public interface SharedWaypointMinimapAdapter {
	String id();

	boolean isAvailable();

	void tick(WaypointSyncGateway gateway, Map<String, SharedWaypointInfo> remoteWaypoints, boolean enabled, SharedWaypointBridgeConfig config);

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