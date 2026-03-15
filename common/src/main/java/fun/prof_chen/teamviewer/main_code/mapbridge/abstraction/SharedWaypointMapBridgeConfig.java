package fun.prof_chen.teamviewer.main_code.mapbridge.abstraction;

public interface SharedWaypointMapBridgeConfig {
	boolean isUploadSharedWaypoints();

	boolean isEnableLongTermWaypoint();

	int getLongTermWaypointTimeoutSeconds();

	int getWaypointTimeoutSeconds();

	boolean isShowSharedWaypoints();

	boolean isShowOwnSharedWaypointsOnMinimap();
}