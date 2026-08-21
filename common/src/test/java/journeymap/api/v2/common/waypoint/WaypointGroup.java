package journeymap.api.v2.common.waypoint;

import java.util.UUID;

/** Minimal JourneyMap API v2 waypoint-group shape used by the Lua adapter tests. */
public final class WaypointGroup {
    private final String guid = UUID.randomUUID().toString();
    private final String modId;
    private final String name;
    private String tag;
    private boolean persistent;

    public WaypointGroup(String modId, String name) {
        this.modId = modId;
        this.name = name;
    }

    public String getGuid() { return guid; }
    public String getModId() { return modId; }
    public String getName() { return name; }
    public String getTag() { return tag; }
    public boolean isPersistent() { return persistent; }
    public void setTag(String value) { tag = value; }
    public void setPersistent(boolean value) { persistent = value; }
    public boolean addWaypoint(Waypoint waypoint) {
        waypoint.setGroupId(guid);
        return true;
    }
}
