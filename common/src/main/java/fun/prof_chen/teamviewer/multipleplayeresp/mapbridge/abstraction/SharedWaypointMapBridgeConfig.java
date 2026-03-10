package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction;

public interface SharedWaypointMapBridgeConfig {
	boolean isUploadSharedWaypoints();

	boolean isEnableLongTermWaypoint();

	int getLongTermWaypointTimeoutSeconds();

	int getWaypointTimeoutSeconds();

	boolean isShowSharedWaypoints();

	boolean isShowOwnSharedWaypointsOnMinimap();
}