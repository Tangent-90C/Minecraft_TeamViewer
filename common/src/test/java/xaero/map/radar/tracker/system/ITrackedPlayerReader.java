package xaero.map.radar.tracker.system;

import java.util.UUID;

public interface ITrackedPlayerReader {
    UUID getId(Object player);
    double getX(Object player);
    double getY(Object player);
    double getZ(Object player);
    Object getDimension(Object player);
}
