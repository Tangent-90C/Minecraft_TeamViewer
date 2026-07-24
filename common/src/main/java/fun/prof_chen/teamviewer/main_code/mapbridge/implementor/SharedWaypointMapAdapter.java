package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;

import java.util.List;

public interface SharedWaypointMapAdapter {
	String id();

	boolean isAvailable();

	/** Lists user-created native waypoints only; managed TeamViewRelay objects are excluded. */
	List<NativeMapWaypointSnapshot> listLocalWaypoints();

	void upsertRemoteWaypoint(MapWaypointCommand command);

	void deleteRemoteWaypoint(String waypointId);

	default void deleteWaypoints(List<String> waypointIds) {
		if (waypointIds == null || waypointIds.isEmpty()) {
			return;
		}
		for (String waypointId : waypointIds) {
			deleteRemoteWaypoint(waypointId);
		}
	}

	void clearRemoteWaypoints();
}
