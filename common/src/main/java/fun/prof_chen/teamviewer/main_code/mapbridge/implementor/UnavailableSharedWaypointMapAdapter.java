package fun.prof_chen.teamviewer.main_code.mapbridge.implementor;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;

import java.util.List;
import java.util.Objects;

/** No-op port used when an optional integration cannot be activated in this runtime. */
public record UnavailableSharedWaypointMapAdapter(
        String id,
        IntegrationSupportStatus supportStatus,
        String supportDetail) implements SharedWaypointMapAdapter {
    public UnavailableSharedWaypointMapAdapter {
        id = Objects.requireNonNull(id, "id");
        supportStatus = Objects.requireNonNull(supportStatus, "supportStatus");
        if (supportStatus == IntegrationSupportStatus.AVAILABLE) {
            throw new IllegalArgumentException("An unavailable adapter cannot report AVAILABLE");
        }
        supportDetail = supportDetail == null ? "" : supportDetail;
    }

    @Override public boolean isAvailable() { return false; }
    @Override public List<NativeMapWaypointSnapshot> listLocalWaypoints() { return List.of(); }
    @Override public void upsertRemoteWaypoint(MapWaypointCommand command) { }
    @Override public void deleteRemoteWaypoint(String waypointId) { }
    @Override public void clearRemoteWaypoints() { }
}
