package journeymap.client.api.display;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/** JourneyMap API 1.x waypoint shape used by the legacy Lua contract test. */
public final class Waypoint {
    private final String guid = UUID.randomUUID().toString();
    private final String modId;
    private final String id;
    private String name;
    private String dimension;
    private BlockPos position;
    private int color;

    public Waypoint(String modId, String id, String name, World.Key dimension, BlockPos position) {
        this(modId, id, name, dimension.toString(), position);
    }

    public Waypoint(String modId, String id, String name, String dimension, BlockPos position) {
        this.modId = modId;
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.position = position;
    }

    public String getGuid() { return guid; }
    public String getModId() { return modId; }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDimension() { return dimension; }
    public BlockPos getPosition() { return position; }
    public Integer getColor() { return color; }
    public Waypoint setPersistent(boolean value) { return this; }
    public void setEnabled(boolean value) { }
    public Waypoint setColor(int value) { color = value; return this; }
    public Waypoint setPosition(String dimension, BlockPos position) {
        this.dimension = dimension;
        this.position = position;
        return this;
    }
    public Waypoint setDirty() { return this; }
}
