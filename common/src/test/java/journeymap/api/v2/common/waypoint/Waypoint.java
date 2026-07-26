package journeymap.api.v2.common.waypoint;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Mutable JourneyMap waypoint test double with the API surface used by built-in Lua. */
public final class Waypoint {
    private final String guid = UUID.randomUUID().toString();
    private final String modId;
    private String name;
    private String primaryDimension;
    private BlockPos position;
    private int color;
    private boolean enabled;
    private boolean persistent;
    private boolean showBeacon = true;
    private boolean showOnMap = true;
    private boolean showInWorld = true;

    public Waypoint(String modId, BlockPos position, String name, String primaryDimension) {
        this.modId = modId;
        this.position = position;
        this.name = name;
        this.primaryDimension = primaryDimension;
    }

    public String getGuid() { return guid; }
    public String getModId() { return modId; }
    public String getName() { return name; }
    public BlockPos getBlockPos() { return position; }
    public String getPrimaryDimension() { return primaryDimension; }
    public int getColor() { return color; }
    public boolean showBeacon() { return showBeacon; }
    public boolean showOnMap() { return showOnMap; }
    public boolean showInWorld() { return showInWorld; }
    public void setPos(int x, int y, int z) { position = new BlockPos(x, y, z); }
    public void setColor(int value) { color = value; }
    public void setEnabled(boolean value) { enabled = value; }
    public void setPersistent(boolean value) { persistent = value; }
    public void setShowBeacon(boolean value) { showBeacon = value; }
    public void setShowOnMap(boolean value) { showOnMap = value; }
    public void setShowInWorld(boolean value) { showInWorld = value; }
}
