package fun.prof_chen.teamviewer.main_code.plugin;

import journeymap.api.v2.common.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.List;

/** In-memory JourneyMap IClientAPI shape used by Lua integration tests. */
public final class JourneyMapApiStub {
    private final List<Waypoint> waypoints = new ArrayList<>();

    public void addWaypoint(String modId, Waypoint value) { waypoints.add(value); }
    public void removeWaypoint(String modId, Waypoint value) { waypoints.remove(value); }
    public List<Waypoint> getAllWaypoints() { return waypoints; }
    public List<Waypoint> waypoints() { return List.copyOf(waypoints); }
}
