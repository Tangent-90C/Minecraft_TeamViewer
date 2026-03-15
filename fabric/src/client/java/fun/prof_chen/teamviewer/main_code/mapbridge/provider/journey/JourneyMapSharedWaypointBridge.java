package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import journeymap.api.v2.common.waypoint.Waypoint;
import fun.prof_chen.teamviewer.main_code.mapbridge.abstraction.SharedWaypointMapBridgeConfig;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class JourneyMapSharedWaypointBridge {
	private static final String SHARED_PREFIX = "[TV] ";
	private static final String SHARED_WAYPOINT_PREFIX = "shared:";
	private static final long LOCAL_SCAN_INTERVAL_MS = 1_500L;
	private static final long REMOTE_SYNC_INTERVAL_MS = 500L;
	private static final Map<String, Waypoint> MANAGED_SHARED_WAYPOINTS = new ConcurrentHashMap<>();
	private static final Map<String, SharedWaypointInfo> KNOWN_LOCAL_WAYPOINTS = new ConcurrentHashMap<>();
	private static final Map<String, Vec3d> TRACKED_ENTITY_LAST_POSITIONS = new ConcurrentHashMap<>();
	private static volatile long lastLocalScanMs = 0L;
	private static volatile long lastRemoteSyncMs = 0L;

	private JourneyMapSharedWaypointBridge() {
	}

	static boolean isAvailable() {
		return JourneyMapWaypointAccess.isAvailable();
	}

	static void tick(WaypointSyncGateway gateway, Map<String, SharedWaypointInfo> remoteWaypoints, boolean enabled,
			SharedWaypointMapBridgeConfig config) {
		if (!enabled) {
			clear();
			return;
		}
		if (!isAvailable() || gateway == null || config == null) {
			return;
		}

		long now = System.currentTimeMillis();
		if (gateway.isConnected() && now - lastLocalScanMs >= LOCAL_SCAN_INTERVAL_MS) {
			lastLocalScanMs = now;
			scanAndSyncLocalWaypoints(gateway, config);
		}

		if (!config.isShowSharedWaypoints()) {
			clearManagedWaypoints();
			return;
		}

		if (now - lastRemoteSyncMs >= REMOTE_SYNC_INTERVAL_MS) {
			lastRemoteSyncMs = now;
			syncRemoteWaypoints(remoteWaypoints == null ? Map.of() : remoteWaypoints, gateway, config);
		}
	}

	static void deleteWaypoint(String waypointId) {
		if (waypointId == null || waypointId.isBlank()) {
			return;
		}
		JourneyMapWaypointAccess.removeWaypoint(MANAGED_SHARED_WAYPOINTS, SHARED_WAYPOINT_PREFIX + waypointId);
		TRACKED_ENTITY_LAST_POSITIONS.remove(waypointId);
	}

	static void clear() {
		clearManagedWaypoints();
		KNOWN_LOCAL_WAYPOINTS.clear();
		TRACKED_ENTITY_LAST_POSITIONS.clear();
	}

	private static void clearManagedWaypoints() {
		JourneyMapWaypointAccess.clearWaypoints(MANAGED_SHARED_WAYPOINTS);
	}

	private static void scanAndSyncLocalWaypoints(WaypointSyncGateway gateway, SharedWaypointMapBridgeConfig config) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return;
		}

		UUID ownerId = client.player.getUuid();
		String ownerName = client.player.getName().getString();
		String dimension = client.world.getRegistryKey().getValue().toString();
		Map<String, SharedWaypointInfo> currentLocal = readCurrentLocalWaypoints(ownerId, ownerName, dimension);

		if (!config.isUploadSharedWaypoints()) {
			KNOWN_LOCAL_WAYPOINTS.clear();
			KNOWN_LOCAL_WAYPOINTS.putAll(currentLocal);
			return;
		}

		Map<String, WaypointSyncPayload> upserts = new HashMap<>();
		List<String> deletes = new ArrayList<>();
		int ttl = config.isEnableLongTermWaypoint()
				? config.getLongTermWaypointTimeoutSeconds()
				: config.getWaypointTimeoutSeconds();

		for (Map.Entry<String, SharedWaypointInfo> entry : currentLocal.entrySet()) {
			if (!KNOWN_LOCAL_WAYPOINTS.containsKey(entry.getKey())) {
				upserts.put(entry.getKey(), WaypointSyncPayload.manual(entry.getValue(), ttl));
			}
		}

		for (String knownId : Set.copyOf(KNOWN_LOCAL_WAYPOINTS.keySet())) {
			if (!currentLocal.containsKey(knownId)) {
				deletes.add(knownId);
			}
		}

		if (!upserts.isEmpty()) {
			gateway.sendWaypointUpserts(ownerId, upserts);
		}
		if (!deletes.isEmpty()) {
			gateway.sendWaypointDeletes(ownerId, deletes);
		}

		KNOWN_LOCAL_WAYPOINTS.clear();
		KNOWN_LOCAL_WAYPOINTS.putAll(currentLocal);
	}

	private static Map<String, SharedWaypointInfo> readCurrentLocalWaypoints(UUID ownerId, String ownerName,
			String dimension) {
		try {
			List<? extends Waypoint> allWaypoints = JourneyMapWaypointAccess.clientApi().getAllWaypoints();
			if (allWaypoints == null || allWaypoints.isEmpty()) {
				return Map.of();
			}

			Map<String, SharedWaypointInfo> currentLocal = new HashMap<>();
			for (Waypoint waypoint : allWaypoints) {
				if (waypoint == null) {
					continue;
				}
				String name = waypoint.getName();
				if (name == null || name.isBlank() || name.startsWith(SHARED_PREFIX)) {
					continue;
				}
				if (JourneyMapClientPlugin.TEAMVIEWER_MOD_ID.equals(waypoint.getModId())) {
					continue;
				}

				int x = waypoint.getBlockPos().getX();
				int y = waypoint.getBlockPos().getY();
				int z = waypoint.getBlockPos().getZ();
				String symbol = name.substring(0, 1).toUpperCase();
				String stableIdSource = ownerId + "|" + dimension + "|" + name + "|" + x + "|" + y + "|" + z;
				String waypointId = UUID.nameUUIDFromBytes(stableIdSource.getBytes(StandardCharsets.UTF_8)).toString();
				SharedWaypointInfo previous = KNOWN_LOCAL_WAYPOINTS.get(waypointId);
				long createdAt = previous != null ? previous.createdAt() : System.currentTimeMillis();
				currentLocal.put(waypointId, new SharedWaypointInfo(
						waypointId,
						ownerId,
						ownerName,
						name,
						symbol,
						x,
						y,
						z,
						dimension,
						waypoint.getColor(),
						createdAt,
						null,
						null,
						null,
						null,
						"manual",
						null,
						"journeymap"));
			}
			return currentLocal;
		} catch (Exception e) {
			return Map.of();
		}
	}

	private static void syncRemoteWaypoints(Map<String, SharedWaypointInfo> remoteWaypoints, WaypointSyncGateway gateway,
			SharedWaypointMapBridgeConfig config) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return;
		}

		UUID localPlayerId = client.player.getUuid();
		String currentDimension = client.world.getRegistryKey().getValue().toString();
		Set<String> activeIds = new HashSet<>();

		for (Map.Entry<String, SharedWaypointInfo> entry : remoteWaypoints.entrySet()) {
			SharedWaypointInfo waypoint = entry.getValue();
			if (waypoint == null || waypoint.waypointId() == null || waypoint.waypointId().isBlank()) {
				continue;
			}
			if (isLocalPlayerTargetedWaypoint(client, waypoint, currentDimension)) {
				continue;
			}
			if (!config.isShowOwnSharedWaypointsOnMinimap() && waypoint.ownerId() != null
					&& waypoint.ownerId().equals(localPlayerId)) {
				continue;
			}
			if (waypoint.dimension() != null && !waypoint.dimension().isBlank()
					&& !Objects.equals(waypoint.dimension(), currentDimension)) {
				continue;
			}

			Vec3d position = resolveWaypointSyncPosition(waypoint, currentDimension, gateway);
			String id = SHARED_WAYPOINT_PREFIX + waypoint.waypointId();
			activeIds.add(id);
			JourneyMapWaypointAccess.upsertWaypoint(
					MANAGED_SHARED_WAYPOINTS,
					id,
					decorateSharedName(waypoint),
					(int) Math.floor(position.x),
					(int) Math.floor(position.y),
					(int) Math.floor(position.z),
					client.world.getRegistryKey(),
					waypoint.color());
		}

		for (String existingId : Set.copyOf(MANAGED_SHARED_WAYPOINTS.keySet())) {
			if (!activeIds.contains(existingId)) {
				JourneyMapWaypointAccess.removeWaypoint(MANAGED_SHARED_WAYPOINTS, existingId);
			}
		}
	}

	private static String decorateSharedName(SharedWaypointInfo waypoint) {
		String ownerName = waypoint.ownerName() == null || waypoint.ownerName().isBlank()
				? "Unknown"
				: waypoint.ownerName().trim();
		String name = waypoint.name() == null || waypoint.name().isBlank() ? "Waypoint" : waypoint.name().trim();
		return SHARED_PREFIX + ownerName + ": " + name;
	}

	private static Vec3d resolveWaypointSyncPosition(SharedWaypointInfo waypoint, String currentDimension,
			WaypointSyncGateway gateway) {
		if (waypoint == null) {
			return Vec3d.ZERO;
		}
		if (!"entity".equalsIgnoreCase(waypoint.targetType()) || waypoint.targetEntityId() == null
				|| waypoint.targetEntityId().isBlank()) {
			return new Vec3d(waypoint.x() + 0.5D, waypoint.y(), waypoint.z() + 0.5D);
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null && Objects.equals(currentDimension, client.world.getRegistryKey().getValue().toString())) {
			for (Entity entity : client.world.getEntities()) {
				if (entity != null && waypoint.targetEntityId().equals(entity.getUuidAsString())) {
					Vec3d pos = entity.getPos();
					TRACKED_ENTITY_LAST_POSITIONS.put(waypoint.waypointId(), pos);
					return pos;
				}
			}
			if (isPlayerTarget(waypoint)) {
				Vec3d playerPos = resolveLocalPlayerPositionFallback(client, waypoint.targetEntityId(), waypoint.targetEntityName());
				if (playerPos != null) {
					TRACKED_ENTITY_LAST_POSITIONS.put(waypoint.waypointId(), playerPos);
					return playerPos;
				}
			}
		}

		if (gateway != null) {
			Position3D remoteEntity = gateway.getRemoteEntityPosition(waypoint.targetEntityId(), currentDimension);
			if (remoteEntity != null) {
				Vec3d pos = new Vec3d(remoteEntity.x(), remoteEntity.y(), remoteEntity.z());
				TRACKED_ENTITY_LAST_POSITIONS.put(waypoint.waypointId(), pos);
				return pos;
			}
			if (isPlayerTarget(waypoint)) {
				Position3D remotePlayer = gateway.getRemotePlayerPosition(
						waypoint.targetEntityId(),
						waypoint.targetEntityName(),
						currentDimension);
				if (remotePlayer != null) {
					Vec3d pos = new Vec3d(remotePlayer.x(), remotePlayer.y(), remotePlayer.z());
					TRACKED_ENTITY_LAST_POSITIONS.put(waypoint.waypointId(), pos);
					return pos;
				}
			}
		}

		Vec3d lastKnown = TRACKED_ENTITY_LAST_POSITIONS.get(waypoint.waypointId());
		if (lastKnown != null) {
			return lastKnown;
		}
		Vec3d initial = new Vec3d(waypoint.x() + 0.5D, waypoint.y(), waypoint.z() + 0.5D);
		TRACKED_ENTITY_LAST_POSITIONS.put(waypoint.waypointId(), initial);
		return initial;
	}

	private static boolean isPlayerTarget(SharedWaypointInfo waypoint) {
		return waypoint.targetEntityType() != null && "minecraft:player".equalsIgnoreCase(waypoint.targetEntityType());
	}

	private static boolean isLocalPlayerTargetedWaypoint(MinecraftClient client, SharedWaypointInfo waypoint,
			String currentDimension) {
		if (client == null || client.player == null || waypoint == null) {
			return false;
		}
		if (waypoint.dimension() != null && !waypoint.dimension().isBlank()
				&& !Objects.equals(waypoint.dimension(), currentDimension)) {
			return false;
		}
		if (!"entity".equalsIgnoreCase(waypoint.targetType())) {
			return false;
		}

		String targetEntityId = waypoint.targetEntityId();
		if (targetEntityId != null && !targetEntityId.isBlank()
				&& targetEntityId.equals(client.player.getUuidAsString())) {
			return true;
		}

		String targetEntityName = waypoint.targetEntityName();
		if (targetEntityName != null && !targetEntityName.isBlank()) {
			String localName = client.player.getName().getString();
			return localName != null && localName.equalsIgnoreCase(targetEntityName);
		}

		return false;
	}

	private static Vec3d resolveLocalPlayerPositionFallback(MinecraftClient client, String targetEntityId,
			String targetEntityName) {
		if (client.world == null) {
			return null;
		}

		UUID expectedUuid = null;
		if (targetEntityId != null && !targetEntityId.isBlank()) {
			try {
				expectedUuid = UUID.fromString(targetEntityId);
			} catch (IllegalArgumentException ignored) {
			}
		}

		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player == null) {
				continue;
			}
			if (expectedUuid != null && expectedUuid.equals(player.getUuid())) {
				return player.getPos();
			}
			if (targetEntityName != null && !targetEntityName.isBlank()) {
				String name = player.getName().getString();
				if (name != null && name.equalsIgnoreCase(targetEntityName)) {
					return player.getPos();
				}
			}
		}

		return null;
	}
}