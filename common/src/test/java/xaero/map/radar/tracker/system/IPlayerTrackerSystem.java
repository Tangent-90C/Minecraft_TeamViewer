package xaero.map.radar.tracker.system;

import java.util.Iterator;

public interface IPlayerTrackerSystem {
    ITrackedPlayerReader getReader();
    Iterator<?> getTrackedPlayerIterator();
}
