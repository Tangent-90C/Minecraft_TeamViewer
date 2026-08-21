package fun.prof_chen.teamviewer.main_code.plugin;

import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointGroup;

import java.util.ArrayList;
import java.util.List;

/** In-memory JourneyMap IClientAPI shape used by Lua integration tests. */
public final class JourneyMapApiStub {
    private final List<Waypoint> waypoints = new ArrayList<>();
    private final List<WaypointGroup> waypointGroups = new ArrayList<>();
    private int suppressedRemovals;

    public void addWaypoint(String modId, Waypoint value) { waypoints.add(value); }
    public void removeWaypoint(String modId, Waypoint value) {
        if (suppressedRemovals > 0) {
            suppressedRemovals--;
            return;
        }
        waypoints.remove(value);
    }
    public void suppressNextRemovals(int count) { suppressedRemovals = Math.max(0, count); }
    public Waypoint getWaypoint(String modId, String guid) {
        return waypoints.stream()
                .filter(value -> modId.equals(value.getModId()) && guid.equals(value.getGuid()))
                .findFirst().orElse(null);
    }
    public WaypointGroup getWaypointGroupByName(String modId, String name) {
        return waypointGroups.stream()
                .filter(value -> modId.equals(value.getModId()) && name.equals(value.getName()))
                .findFirst().orElse(null);
    }
    public void addWaypointGroup(WaypointGroup value) { waypointGroups.add(value); }
    public List<Waypoint> getAllWaypoints() { return waypoints; }
    public List<Waypoint> waypoints() { return List.copyOf(waypoints); }
    public List<WaypointGroup> waypointGroups() { return List.copyOf(waypointGroups); }
}
