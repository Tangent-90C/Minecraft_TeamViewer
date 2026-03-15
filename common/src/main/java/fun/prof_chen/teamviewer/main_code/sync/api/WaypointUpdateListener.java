package fun.prof_chen.teamviewer.main_code.sync.api;

import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;

import java.util.List;
import java.util.Map;

public interface WaypointUpdateListener {
	void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints);

	default void onWaypointsDeleted(List<String> waypointIds) {
	}
}