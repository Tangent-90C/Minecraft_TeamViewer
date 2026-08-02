package journeymap.api.v2.common.waypoint;

import net.minecraft.util.math.BlockPos;

/** Concrete test shape for API v2 families that expose presentation controls. */
public final class FullWaypoint extends Waypoint {
    private boolean showBeacon = true;
    private boolean showOnMap = true;
    private boolean showInWorld = true;

    public FullWaypoint(String modId, BlockPos position, String name, String primaryDimension) {
        super(modId, position, name, primaryDimension);
    }

    public boolean showBeacon() { return showBeacon; }
    public boolean showOnMap() { return showOnMap; }
    public boolean showInWorld() { return showInWorld; }
    public void setShowBeacon(boolean value) { showBeacon = value; }
    public void setShowOnMap(boolean value) { showOnMap = value; }
    public void setShowInWorld(boolean value) { showInWorld = value; }
}
