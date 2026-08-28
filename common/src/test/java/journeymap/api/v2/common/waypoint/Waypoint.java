package journeymap.api.v2.common.waypoint;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mutable JourneyMap waypoint test double with the API surface used by built-in Lua. */
public class Waypoint {
    private final String guid = UUID.randomUUID().toString();
    private final String modId;
    private String name;
    private String primaryDimension;
    private String groupId;
    private BlockPos position;
    private int color;
    private boolean enabled;
    private boolean persistent;
    private int mutationCount;
    private final Map<String, String> customData = new HashMap<>();

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
    public String getGroupId() { return groupId; }
    public int getColor() { return color; }
    public int getMutationCount() { return mutationCount; }
    public String getCustomData(String key) { return customData.get(key); }
    public Map<String, String> getCustomDataMap() { return Map.copyOf(customData); }
    public void setPos(int x, int y, int z) { position = new BlockPos(x, y, z); mutationCount++; }
    public void setColor(int value) { color = value; mutationCount++; }
    public void setEnabled(boolean value) { enabled = value; mutationCount++; }
    public void setPersistent(boolean value) { persistent = value; mutationCount++; }
    public void setGroupId(String value) { groupId = value; mutationCount++; }
    public void setCustomData(String key, String value) { customData.put(key, value); mutationCount++; }
}
