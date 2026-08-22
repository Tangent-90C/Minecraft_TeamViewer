package journeymap.api.v2.common.waypoint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** JourneyMap factory test double resolved by Lua. */
public final class WaypointFactory {
    private WaypointFactory() { }

    public static Waypoint createClientWaypoint(
            String modId, BlockPos position, String name, World.Key dimension, boolean persistent) {
        Waypoint value = new FullWaypoint(modId, position, name, dimension.toString());
        value.setPersistent(persistent);
        return value;
    }

    public static Waypoint createWaypoint(
            String modId, BlockPos position, String name, World.Key dimension, boolean persistent) {
        return createClientWaypoint(modId, position, name, dimension, persistent);
    }

    public static WaypointGroup createWaypointGroup(String modId, String name) {
        return new WaypointGroup(modId, name);
    }

    public static WaypointGroup fromGroupJsonString(String json) {
        JsonObject value = JsonParser.parseString(json).getAsJsonObject();
        WaypointGroup group = new WaypointGroup(
                value.get("modId").getAsString(), value.get("name").getAsString(),
                value.get("guid").getAsString());
        JsonObject settings = value.getAsJsonObject("settings");
        if (settings != null && settings.has("persistent")) {
            group.setPersistent(settings.get("persistent").getAsBoolean());
        }
        if (value.has("tag")) group.setTag(value.get("tag").getAsString());
        return group;
    }
}
