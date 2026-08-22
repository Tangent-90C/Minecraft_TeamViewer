package journeymap.api.v2.common.waypoint;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal JourneyMap API v2 waypoint-group shape used by the Lua adapter tests. */
public final class WaypointGroup {
    private final String guid;
    private final String modId;
    private final String name;
    private String tag;
    private boolean persistent;
    private final Map<String, String> customData = new LinkedHashMap<>();

    public WaypointGroup(String modId, String name) {
        this(modId, name, UUID.randomUUID().toString());
    }

    public WaypointGroup(String modId, String name, String guid) {
        this.modId = modId;
        this.name = name;
        this.guid = guid;
    }

    public String getGuid() { return guid; }
    public String getModId() { return modId; }
    public String getName() { return name; }
    public String getTag() { return tag; }
    public boolean isPersistent() { return persistent; }
    public void setTag(String value) { tag = value; }
    public void setPersistent(boolean value) { persistent = value; }
    public void setCustomData(String key, String value) { customData.put(key, value); }
    public String getCustomData(String key) { return customData.get(key); }
    public Map<String, String> getCustomDataMap() { return Map.copyOf(customData); }
    public boolean addWaypoint(Waypoint waypoint) {
        waypoint.setGroupId(guid);
        return true;
    }
}
