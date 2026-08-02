package fun.prof_chen.teamviewer.main_code.plugin;

import journeymap.client.api.display.Waypoint;

import java.util.ArrayList;
import java.util.List;

/** In-memory JourneyMap API 1.x displayable service. */
public final class JourneyMapV1ApiStub {
    private final List<Waypoint> waypoints = new ArrayList<>();

    public void show(Waypoint waypoint) { waypoints.add(waypoint); }
    public void remove(Waypoint waypoint) { waypoints.remove(waypoint); }
    public List<Waypoint> getAllWaypoints() { return waypoints; }
    public List<Waypoint> waypoints() { return List.copyOf(waypoints); }
}
