package fun.prof_chen.teamviewer.main_code.plugin;

import journeymap.api.v2.common.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.List;

/** Models JourneyMap's real enum singleton ClientAPI implementation. */
public enum EnumJourneyMapApiStub {
    INSTANCE;

    private final List<Waypoint> waypoints = new ArrayList<>();

    public void reset() { waypoints.clear(); }
    public void addWaypoint(String modId, Waypoint value) { waypoints.add(value); }
    public void removeWaypoint(String modId, Waypoint value) { waypoints.remove(value); }
    public List<Waypoint> getAllWaypoints() { return waypoints; }
}
