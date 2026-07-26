package journeymap.api.v2.common.waypoint;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** JourneyMap factory test double resolved by Lua. */
public final class WaypointFactory {
    private WaypointFactory() { }

    public static Waypoint createClientWaypoint(
            String modId, BlockPos position, String name, World.Key dimension, boolean persistent) {
        Waypoint value = new Waypoint(modId, position, name, dimension.toString());
        value.setPersistent(persistent);
        return value;
    }
}
