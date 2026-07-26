package xaero.map;

import xaero.map.radar.tracker.system.IPlayerTrackerSystem;

/** Captures the tracker proxy registered by Lua. */
public final class PlayerTrackerSystemManager {
    private IPlayerTrackerSystem system;

    public void register(String id, IPlayerTrackerSystem value) { system = value; }
    public IPlayerTrackerSystem system() { return system; }
}
