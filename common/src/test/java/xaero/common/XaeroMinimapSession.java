package xaero.common;

import xaero.common.minimap.waypoints.Waypoint;

import java.util.ArrayList;
import java.util.List;

/** Session graph test double matching the reflection path used by the Lua adapter. */
public final class XaeroMinimapSession {
    private static Session session = new Session();

    private XaeroMinimapSession() { }

    public static Outer getCurrentSession() { return new Outer(session); }
    public static WaypointSet waypointSet() { return session.manager.world.set; }
    public static void reset() { session = new Session(); }

    public record Outer(Session session) {
        public Processor getMinimapProcessor() { return new Processor(session); }
    }
    public record Processor(Session session) {
        public Session getSession() { return session; }
    }
    public static final class Session {
        private final WorldManager manager = new WorldManager();
        private final WorldManagerIO io = new WorldManagerIO();
        public WorldManager getWorldManager() { return manager; }
        public WorldManagerIO getWorldManagerIO() { return io; }
        public void setSetChangedTime(long value) { }
    }
    public static final class WorldManager {
        private final WaypointWorld world = new WaypointWorld();
        public WaypointWorld getCurrentWorld() { return world; }
    }
    public static final class WaypointWorld {
        private final WaypointSet set = new WaypointSet();
        public WaypointSet getCurrentWaypointSet() { return set; }
    }
    public static final class WaypointSet {
        private final List<Waypoint> values = new ArrayList<>();
        public List<Waypoint> getWaypoints() { return values; }
        public void add(Waypoint value) { values.add(value); }
        public void remove(Waypoint value) { values.remove(value); }
    }
    public static final class WorldManagerIO {
        public void saveWorld(WaypointWorld world) { }
    }
}
