package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import journeymap.api.v2.common.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Minecraft 1.21.8 JourneyMap native waypoint CRUD port. */
public final class JourneyMapSharedWaypointAdapter implements SharedWaypointMapAdapter {
    private final Map<String, Waypoint> managed = new ConcurrentHashMap<>();

    @Override
    public String id() { return "journeymap-shared-waypoints"; }

    @Override
    public boolean isAvailable() { return JourneyMapWaypointAccess.isAvailable(); }

    @Override
    public IntegrationSupportStatus supportStatus() { return JourneyMapClientPlugin.supportStatus(); }

    @Override
    public List<NativeMapWaypointSnapshot> listLocalWaypoints() {
        if (!isAvailable()) return List.of();
        List<NativeMapWaypointSnapshot> result = new ArrayList<>();
        for (Waypoint value : JourneyMapWaypointAccess.clientApi().getAllWaypoints()) {
            if (value == null || JourneyMapClientPlugin.TEAMVIEWER_MOD_ID.equals(value.getModId())) continue;
            var pos = value.getBlockPos();
            result.add(new NativeMapWaypointSnapshot(value.getGuid(), value.getName(), firstSymbol(value.getName()),
                    pos.getX(), pos.getY(), pos.getZ(), null, value.getColor()));
        }
        return result;
    }

    @Override
    public void upsertRemoteWaypoint(MapWaypointCommand command) {
        if (command == null) return;
        JourneyMapWaypointAccess.upsertWaypoint(managed, command.waypointId(), command.name(),
                command.x(), command.y(), command.z(), JourneyMapWaypointAccess.parseDimension(command.dimension()), command.color());
    }

    @Override
    public void deleteRemoteWaypoint(String waypointId) {
        JourneyMapWaypointAccess.removeWaypoint(managed, waypointId);
    }

    @Override
    public void clearRemoteWaypoints() {
        JourneyMapWaypointAccess.clearWaypoints(managed);
    }

    private static String firstSymbol(String value) {
        return value == null || value.isBlank() ? "W" : value.substring(0, 1).toUpperCase();
    }
}
