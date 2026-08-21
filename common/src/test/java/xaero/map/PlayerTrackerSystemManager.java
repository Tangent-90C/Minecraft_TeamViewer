package xaero.map;

import xaero.map.radar.tracker.system.IPlayerTrackerSystem;

import java.util.LinkedHashMap;
import java.util.Map;

/** Captures the tracker proxy registered by Lua. */
public final class PlayerTrackerSystemManager {
    private IPlayerTrackerSystem system;
    private final Map<String, IPlayerTrackerSystem> systems = new LinkedHashMap<>();

    public void register(String id, IPlayerTrackerSystem value) {
        system = value;
        systems.put(id, value);
    }
    public IPlayerTrackerSystem system() { return system; }
    public IPlayerTrackerSystem system(String id) { return systems.get(id); }
}
