package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;

import java.util.List;
import java.util.Objects;

/** Explicit no-op shared-waypoint port for an unsupported optional map integration. */
public record UnsupportedSharedWaypointMapAdapter(String id, String supportDetail)
        implements SharedWaypointMapAdapter {
    public UnsupportedSharedWaypointMapAdapter {
        id = Objects.requireNonNull(id, "id");
        supportDetail = supportDetail == null ? "unsupported Minecraft version" : supportDetail;
    }

    @Override public boolean isAvailable() { return false; }
    @Override public IntegrationSupportStatus supportStatus() { return IntegrationSupportStatus.UNSUPPORTED_VERSION; }
    @Override public List<NativeMapWaypointSnapshot> listLocalWaypoints() { return List.of(); }
    @Override public void upsertRemoteWaypoint(MapWaypointCommand command) { }
    @Override public void deleteRemoteWaypoint(String waypointId) { }
    @Override public void clearRemoteWaypoints() { }
}
