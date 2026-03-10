package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.journey;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

final class JourneyMapWaypointAccess {
	private static final Logger LOGGER = LoggerFactory.getLogger(JourneyMapWaypointAccess.class);

	private JourneyMapWaypointAccess() {
	}

	static boolean isAvailable() {
		return JourneyMapClientPlugin.isAvailable();
	}

	static void upsertWaypoint(Map<String, Waypoint> managedWaypoints, String id, String name, int x, int y, int z,
			RegistryKey<World> dimension, int color) {
		if (!isAvailable() || managedWaypoints == null || id == null || id.isBlank() || dimension == null) {
			return;
		}

		Waypoint existing = managedWaypoints.get(id);
		if (existing != null) {
			BlockPos currentPos = existing.getBlockPos();
			boolean nameChanged = !Objects.equals(existing.getName(), name);
			boolean positionChanged = currentPos.getX() != x || currentPos.getY() != y || currentPos.getZ() != z;
			if (nameChanged) {
				removeWaypoint(managedWaypoints, id);
				existing = null;
			} else {
				existing.setColor(color);
				existing.setEnabled(true);
				existing.setPersistent(false);
				if (positionChanged) {
					existing.setPos(x, y, z);
				}
				return;
			}
		}

		try {
			Waypoint waypoint = WaypointFactory.createClientWaypoint(
					JourneyMapClientPlugin.TEAMVIEWER_MOD_ID,
					new BlockPos(x, y, z),
					name,
					dimension,
					false);
			if (waypoint == null) {
				return;
			}
			waypoint.setColor(color);
			waypoint.setEnabled(true);
			waypoint.setPersistent(false);
			clientApi().addWaypoint(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, waypoint);
			managedWaypoints.put(id, waypoint);
		} catch (Exception e) {
			LOGGER.debug("Failed to upsert JourneyMap waypoint {}: {}", id, e.getMessage());
		}
	}

	static void removeWaypoint(Map<String, Waypoint> managedWaypoints, String id) {
		if (!isAvailable() || managedWaypoints == null || id == null || id.isBlank()) {
			return;
		}
		Waypoint waypoint = managedWaypoints.remove(id);
		if (waypoint == null) {
			return;
		}
		try {
			clientApi().removeWaypoint(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, waypoint);
		} catch (Exception e) {
			LOGGER.debug("Failed to remove JourneyMap waypoint {}: {}", id, e.getMessage());
		}
	}

	static void clearWaypoints(Map<String, Waypoint> managedWaypoints) {
		if (managedWaypoints == null || managedWaypoints.isEmpty()) {
			return;
		}
		for (String id : new ArrayList<>(managedWaypoints.keySet())) {
			removeWaypoint(managedWaypoints, id);
		}
	}

	static RegistryKey<World> parseDimension(String dimension) {
		if (dimension == null || dimension.isBlank()) {
			return null;
		}
		try {
			Identifier identifier = dimension.contains(":")
					? Identifier.of(dimension)
					: Identifier.of("minecraft:" + dimension.replace("minecraft:", ""));
			if (identifier.equals(World.OVERWORLD.getValue())) {
				return World.OVERWORLD;
			}
			if (identifier.equals(World.NETHER.getValue())) {
				return World.NETHER;
			}
			if (identifier.equals(World.END.getValue())) {
				return World.END;
			}
			return RegistryKey.of(RegistryKeys.WORLD, identifier);
		} catch (Exception e) {
			return null;
		}
	}

	static IClientAPI clientApi() {
		return JourneyMapClientPlugin.clientApi();
	}
}