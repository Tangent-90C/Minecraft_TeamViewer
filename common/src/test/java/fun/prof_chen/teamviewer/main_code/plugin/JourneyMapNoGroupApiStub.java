package fun.prof_chen.teamviewer.main_code.plugin;

import journeymap.api.v2.common.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.List;

/** API v2-shaped service that intentionally omits waypoint-group operations. */
public final class JourneyMapNoGroupApiStub {
    private final List<Waypoint> waypoints = new ArrayList<>();

    public void addWaypoint(String modId, Waypoint value) { waypoints.add(value); }
    public void removeWaypoint(String modId, Waypoint value) { waypoints.remove(value); }
    public Waypoint getWaypoint(String modId, String guid) {
        return waypoints.stream()
                .filter(value -> modId.equals(value.getModId()) && guid.equals(value.getGuid()))
                .findFirst().orElse(null);
    }
    public List<Waypoint> getAllWaypoints() { return waypoints; }
    public List<Waypoint> waypoints() { return List.copyOf(waypoints); }
}
