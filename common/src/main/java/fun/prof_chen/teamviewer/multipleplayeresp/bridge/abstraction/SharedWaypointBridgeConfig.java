package fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction;

public interface SharedWaypointBridgeConfig {
	boolean isUploadSharedWaypoints();

	boolean isEnableLongTermWaypoint();

	int getLongTermWaypointTimeoutSeconds();

	int getWaypointTimeoutSeconds();

	boolean isShowSharedWaypoints();

	boolean isShowOwnSharedWaypointsOnMinimap();
}