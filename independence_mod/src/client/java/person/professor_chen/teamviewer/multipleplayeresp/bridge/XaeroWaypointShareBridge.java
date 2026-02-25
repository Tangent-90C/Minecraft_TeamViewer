package person.professor_chen.teamviewer.multipleplayeresp.bridge;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import person.professor_chen.teamviewer.multipleplayeresp.config.Config;
import person.professor_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;
import person.professor_chen.teamviewer.multipleplayeresp.network.PlayerESPNetworkManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

public final class XaeroWaypointShareBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger(XaeroWaypointShareBridge.class);
	private static final String XAERO_MINIMAP_MOD_ID = "xaerominimap";
	private static final String SHARED_PREFIX = "[TV] ";
	private static final long LOCAL_SCAN_INTERVAL_MS = 1_500L;
	private static final long REMOTE_SYNC_INTERVAL_MS = 800L;

	private static final Map<String, SharedWaypointInfo> knownLocalWaypoints = new ConcurrentHashMap<>();
	private static final Map<String, SharedWaypointInfo> latestRemoteWaypoints = new ConcurrentHashMap<>();
	private static final Map<String, Object> appliedRemoteWaypointObjects = new ConcurrentHashMap<>();
	private static final Map<String, Vec3d> trackedEntityWaypointLastPositions = new ConcurrentHashMap<>();
	private static final Map<String, SharedWaypointInfo> pendingRemoteUpserts = new ConcurrentHashMap<>();
	private static final Set<String> pendingRemoteDeletes = ConcurrentHashMap.newKeySet();

	private static volatile PlayerESPNetworkManager boundNetworkManager;
	private static volatile boolean listenerRegistered = false;
	private static volatile boolean baselineInitialized = false;
	private static volatile boolean remoteReconcileRequired = true;
	private static volatile long lastLocalScanMs = 0L;
	private static volatile long lastRemoteSyncMs = 0L;

	private static final PlayerESPNetworkManager.WaypointUpdateListener WAYPOINT_LISTENER = new PlayerESPNetworkManager.WaypointUpdateListener() {
		@Override
		public void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints) {
			if (waypoints == null || waypoints.isEmpty()) {
				return;
			}
			for (Map.Entry<String, SharedWaypointInfo> entry : waypoints.entrySet()) {
				if (entry.getValue() != null) {
					pendingRemoteUpserts.put(entry.getKey(), entry.getValue());
				}
			}
		}

		@Override
		public void onWaypointsDeleted(List<String> waypointIds) {
			if (waypointIds == null || waypointIds.isEmpty()) {
				return;
			}
			pendingRemoteDeletes.addAll(waypointIds);
		}
	};

	private XaeroWaypointShareBridge() {
	}

	public static void deleteSharedWaypoint(String waypointId) {
		if (waypointId == null || waypointId.isBlank()) {
			return;
		}
		pendingRemoteDeletes.add(waypointId);
		latestRemoteWaypoints.remove(waypointId);
		pendingRemoteUpserts.remove(waypointId);
		trackedEntityWaypointLastPositions.remove(waypointId);
	}

	public static void deleteSharedWaypoints(List<String> waypointIds) {
		if (waypointIds == null || waypointIds.isEmpty()) {
			return;
		}
		for (String waypointId : waypointIds) {
			deleteSharedWaypoint(waypointId);
		}
	}

	public static void tick(PlayerESPNetworkManager networkManager, boolean espEnabled, Config config) {
		if (!espEnabled || networkManager == null || config == null) {
			return;
		}
		if (!FabricLoader.getInstance().isModLoaded(XAERO_MINIMAP_MOD_ID)) {
			return;
		}

		ensureWaypointListener(networkManager);
		long now = System.currentTimeMillis();
		if (!networkManager.isConnected()) {
			remoteReconcileRequired = true;
		}

		if (networkManager.isConnected() && now - lastLocalScanMs >= LOCAL_SCAN_INTERVAL_MS) {
			lastLocalScanMs = now;
			scanAndSyncLocalWaypoints(networkManager, config);
		}

		if (networkManager.isConnected() && now - lastRemoteSyncMs >= REMOTE_SYNC_INTERVAL_MS) {
			lastRemoteSyncMs = now;
			syncRemoteWaypoints(config);
		}
	}

	private static void ensureWaypointListener(PlayerESPNetworkManager networkManager) {
		if (boundNetworkManager != networkManager) {
			if (boundNetworkManager != null && listenerRegistered) {
				boundNetworkManager.removeWaypointUpdateListener(WAYPOINT_LISTENER);
			}
			boundNetworkManager = networkManager;
			listenerRegistered = false;
			remoteReconcileRequired = true;
		}

		if (!listenerRegistered) {
			networkManager.addWaypointUpdateListener(WAYPOINT_LISTENER);
			listenerRegistered = true;
		}
	}

	private static void scanAndSyncLocalWaypoints(PlayerESPNetworkManager networkManager, Config config) {
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) {
				return;
			}

			UUID ownerId = client.player.getUuid();
			String ownerName = client.player.getName().getString();
			String dimension = client.world.getRegistryKey().getValue().toString();

			Map<String, SharedWaypointInfo> currentLocal = readCurrentLocalWaypoints(ownerId, ownerName, dimension);
			if (!baselineInitialized) {
				knownLocalWaypoints.clear();
				knownLocalWaypoints.putAll(currentLocal);
				baselineInitialized = true;
				return;
			}

			if (!config.isUploadSharedWaypoints()) {
				knownLocalWaypoints.clear();
				knownLocalWaypoints.putAll(currentLocal);
				return;
			}

			Map<String, Map<String, Object>> toUpload = new HashMap<>();
			List<String> toDelete = new ArrayList<>();
			int manualTtl = config.isEnableLongTermWaypoint()
					? config.getLongTermWaypointTimeoutSeconds()
					: config.getWaypointTimeoutSeconds();

			for (Map.Entry<String, SharedWaypointInfo> entry : currentLocal.entrySet()) {
				if (!knownLocalWaypoints.containsKey(entry.getKey())) {
					SharedWaypointInfo waypoint = entry.getValue();
					Map<String, Object> payload = new HashMap<>();
					payload.put("x", waypoint.x());
					payload.put("y", waypoint.y());
					payload.put("z", waypoint.z());
					payload.put("dimension", waypoint.dimension());
					payload.put("name", waypoint.name());
					payload.put("symbol", waypoint.symbol());
					payload.put("color", waypoint.color());
					payload.put("ownerId", ownerId.toString());
					payload.put("ownerName", ownerName);
					payload.put("createdAt", waypoint.createdAt());
					payload.put("ttlSeconds", manualTtl);
					payload.put("waypointKind", "manual");
					toUpload.put(entry.getKey(), payload);
				}
			}

			for (String knownId : knownLocalWaypoints.keySet()) {
				if (!currentLocal.containsKey(knownId)) {
					toDelete.add(knownId);
				}
			}

			if (!toUpload.isEmpty()) {
				networkManager.sendWaypointsUpdate(ownerId, toUpload);
			}
			if (!toDelete.isEmpty()) {
				networkManager.sendWaypointsDelete(ownerId, toDelete);
			}

			knownLocalWaypoints.clear();
			knownLocalWaypoints.putAll(currentLocal);
		} catch (Exception e) {
			LOGGER.error("Failed to sync local waypoints: {}", e.getMessage());
		}
	}

	private static void syncRemoteWaypoints(Config config) {
		try {
			Object minimapSession = getMinimapSession();
			if (minimapSession == null) {
				return;
			}
			Object worldManager = invokeNoArg(minimapSession, "getWorldManager");
			Object currentWorld = invokeNoArg(worldManager, "getCurrentWorld");
			if (currentWorld == null) {
				return;
			}
			Object currentWaypointSet = invokeNoArg(currentWorld, "getCurrentWaypointSet");
			if (currentWaypointSet == null) {
				return;
			}

			for (String deletedId : new ArrayList<>(pendingRemoteDeletes)) {
				latestRemoteWaypoints.remove(deletedId);
				removeRemoteWaypointById(currentWaypointSet, deletedId);
				pendingRemoteDeletes.remove(deletedId);
				pendingRemoteUpserts.remove(deletedId);
			}

			if (!pendingRemoteUpserts.isEmpty()) {
				latestRemoteWaypoints.putAll(pendingRemoteUpserts);
				pendingRemoteUpserts.clear();
			}

			if (!config.isShowSharedWaypoints()) {
				boolean changed = removeAllSharedWaypoints(currentWaypointSet);
				if (changed) {
					saveWaypointWorld(minimapSession, currentWorld);
				}
				return;
			}

			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) {
				return;
			}

			UUID localPlayerId = client.player.getUuid();
			String currentDimension = client.world.getRegistryKey().getValue().toString();
			boolean includeOwnSharedWaypoints = config.isShowOwnSharedWaypointsOnMinimap();
			boolean changed = false;
			Set<String> keepIds = new HashSet<>();
			Map<String, String> decoratedNameToId = new HashMap<>();

			if (remoteReconcileRequired) {
				changed |= removeAllSharedWaypoints(currentWaypointSet);
				appliedRemoteWaypointObjects.clear();
				remoteReconcileRequired = false;
			}

			for (Map.Entry<String, SharedWaypointInfo> entry : latestRemoteWaypoints.entrySet()) {
				SharedWaypointInfo waypoint = entry.getValue();
				if (waypoint == null) {
					continue;
				}
				if (!includeOwnSharedWaypoints && waypoint.ownerId() != null && waypoint.ownerId().equals(localPlayerId)) {
					continue;
				}
				if (waypoint.dimension() != null && !waypoint.dimension().isBlank()
						&& !Objects.equals(waypoint.dimension(), currentDimension)) {
					continue;
				}
				decoratedNameToId.put(decorateSharedName(waypoint), entry.getKey());
			}

			changed |= reconcileExistingSharedWaypointsByName(currentWaypointSet, decoratedNameToId);

			for (Map.Entry<String, SharedWaypointInfo> entry : latestRemoteWaypoints.entrySet()) {
				String waypointId = entry.getKey();
				SharedWaypointInfo waypoint = entry.getValue();
				if (waypoint == null) {
					continue;
				}
				if (!includeOwnSharedWaypoints && waypoint.ownerId() != null && waypoint.ownerId().equals(localPlayerId)) {
					continue;
				}
				if (waypoint.dimension() != null && !waypoint.dimension().isBlank()
						&& !Objects.equals(waypoint.dimension(), currentDimension)) {
					continue;
				}

				keepIds.add(waypointId);
				Vec3d trackedPos = resolveWaypointSyncPosition(waypoint, currentDimension);
				SharedWaypointInfo renderWaypoint = waypoint;
				if (trackedPos != null) {
					renderWaypoint = copyWaypointWithPosition(waypoint,
							(int) Math.floor(trackedPos.x),
							(int) Math.floor(trackedPos.y),
							(int) Math.floor(trackedPos.z));
				}
				String decoratedName = decorateSharedName(renderWaypoint);

				Object existingObject = appliedRemoteWaypointObjects.get(waypointId);
				if (existingObject != null && containsWaypointObject(currentWaypointSet, existingObject)) {
					if (!waypointPositionChanged(existingObject, renderWaypoint.x(), renderWaypoint.y(), renderWaypoint.z())) {
						continue;
					}
					removeRemoteWaypointById(currentWaypointSet, waypointId);
					changed = true;
				} else {
					appliedRemoteWaypointObjects.remove(waypointId);
				}

				if (removeSharedWaypointsByExactName(currentWaypointSet, decoratedName, null)) {
					changed = true;
				}

				Object newWaypoint = createXaeroWaypoint(decoratedName, renderWaypoint);
				if (newWaypoint == null) {
					continue;
				}
				invokeSingleArg(currentWaypointSet, "add", newWaypoint);
				appliedRemoteWaypointObjects.put(waypointId, newWaypoint);
				changed = true;
			}

			for (String appliedId : new ArrayList<>(appliedRemoteWaypointObjects.keySet())) {
				if (keepIds.contains(appliedId)) {
					continue;
				}
				if (removeRemoteWaypointById(currentWaypointSet, appliedId)) {
					changed = true;
				}
			}

			if (changed) {
				saveWaypointWorld(minimapSession, currentWorld);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to sync remote waypoints: {}", e.getMessage());
		}
	}

	private static boolean reconcileExistingSharedWaypointsByName(Object waypointSet, Map<String, String> decoratedNameToId) {
		boolean changed = false;
		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return false;
			}

			List<Object> staleObjects = new ArrayList<>();
			Map<String, Object> firstMatchedObjectById = new HashMap<>();
			for (Object waypointObject : waypoints) {
				if (waypointObject == null) {
					continue;
				}
				String name = stringValue(invokeNoArg(waypointObject, "getName"));
				if (name == null || !name.startsWith(SHARED_PREFIX)) {
					continue;
				}

				String matchedId = decoratedNameToId.get(name);
				if (matchedId == null) {
					staleObjects.add(waypointObject);
					continue;
				}

				Object firstObject = firstMatchedObjectById.get(matchedId);
				if (firstObject == null) {
					firstMatchedObjectById.put(matchedId, waypointObject);
					appliedRemoteWaypointObjects.put(matchedId, waypointObject);
					continue;
				}

				if (firstObject != waypointObject) {
					staleObjects.add(waypointObject);
				}
			}

			for (Object staleObject : staleObjects) {
				invokeSingleArg(waypointSet, "remove", staleObject);
				changed = true;
			}
		} catch (Exception e) {
			LOGGER.debug("Failed to reconcile existing shared waypoints: {}", e.getMessage());
		}
		return changed;
	}

	private static boolean removeSharedWaypointsByExactName(Object waypointSet, String exactName, Object excludeObject) {
		if (waypointSet == null || exactName == null || exactName.isBlank()) {
			return false;
		}

		boolean changed = false;
		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return false;
			}

			List<Object> toRemove = new ArrayList<>();
			for (Object waypointObject : waypoints) {
				if (waypointObject == null || waypointObject == excludeObject) {
					continue;
				}
				String name = stringValue(invokeNoArg(waypointObject, "getName"));
				if (exactName.equals(name)) {
					toRemove.add(waypointObject);
				}
			}

			for (Object waypointObject : toRemove) {
				invokeSingleArg(waypointSet, "remove", waypointObject);
				changed = true;
			}
		} catch (Exception e) {
			LOGGER.debug("Failed to remove duplicated shared waypoints by name: {}", e.getMessage());
		}

		return changed;
	}

	private static Map<String, SharedWaypointInfo> readCurrentLocalWaypoints(UUID ownerId, String ownerName,
			String dimension) throws Exception {
		Object minimapSession = getMinimapSession();
		if (minimapSession == null) {
			return Map.of();
		}
		Object worldManager = invokeNoArg(minimapSession, "getWorldManager");
		Object currentWorld = invokeNoArg(worldManager, "getCurrentWorld");
		if (currentWorld == null) {
			return Map.of();
		}
		Object currentWaypointSet = invokeNoArg(currentWorld, "getCurrentWaypointSet");
		if (currentWaypointSet == null) {
			return Map.of();
		}
		Object iterableObject = invokeNoArg(currentWaypointSet, "getWaypoints");
		if (!(iterableObject instanceof Iterable<?> waypoints)) {
			return Map.of();
		}

		Set<Object> remoteObjectRefs = new HashSet<>(appliedRemoteWaypointObjects.values());
		Map<String, SharedWaypointInfo> result = new HashMap<>();
		for (Object waypointObject : waypoints) {
			if (waypointObject == null || remoteObjectRefs.contains(waypointObject)) {
				continue;
			}

			String name = stringValue(invokeNoArg(waypointObject, "getName"));
			if (name == null || name.isBlank() || name.startsWith(SHARED_PREFIX)) {
				continue;
			}

			int x = intValue(invokeNoArg(waypointObject, "getX"), 0);
			int y = intValue(invokeNoArg(waypointObject, "getY"), 64);
			int z = intValue(invokeNoArg(waypointObject, "getZ"), 0);
			int color = intValue(invokeNoArg(waypointObject, "getColor"), 0x55FF55);
			String symbol = safeSymbol(stringValue(invokeNoArg(waypointObject, "getSymbol")));
			long createdAt = longValue(invokeNoArg(waypointObject, "getCreatedAt"), System.currentTimeMillis());

			String idSource = ownerId + "|" + createdAt + "|" + dimension + "|" + x + "|" + y + "|" + z;
			String waypointId = UUID.nameUUIDFromBytes(idSource.getBytes(StandardCharsets.UTF_8)).toString();
			result.put(waypointId, new SharedWaypointInfo(
					waypointId,
					ownerId,
					ownerName,
					name,
					symbol,
					x,
					y,
					z,
					dimension,
					color,
					createdAt,
					null,
					null,
					null,
					null,
					"manual"));
		}

		return result;
	}

	private static boolean removeRemoteWaypointById(Object waypointSet, String waypointId) {
		Object existingObject = appliedRemoteWaypointObjects.remove(waypointId);
		if (existingObject == null) {
			return false;
		}
		try {
			invokeSingleArg(waypointSet, "remove", existingObject);
			return true;
		} catch (Exception e) {
			LOGGER.warn("Failed to remove remote waypoint {}: {}", waypointId, e.getMessage());
			return false;
		}
	}

	private static void removeAllAppliedRemoteWaypoints(Object waypointSet) {
		for (String waypointId : new ArrayList<>(appliedRemoteWaypointObjects.keySet())) {
			removeRemoteWaypointById(waypointSet, waypointId);
		}
	}

	private static boolean removeAllSharedWaypoints(Object waypointSet) {
		boolean changed = false;
		changed |= removeAllAppliedRemoteWaypointsTracked(waypointSet);
		changed |= removeAllPrefixedSharedWaypoints(waypointSet);
		return changed;
	}

	private static boolean removeAllAppliedRemoteWaypointsTracked(Object waypointSet) {
		boolean changed = false;
		for (String waypointId : new ArrayList<>(appliedRemoteWaypointObjects.keySet())) {
			if (removeRemoteWaypointById(waypointSet, waypointId)) {
				changed = true;
			}
		}
		return changed;
	}

	private static boolean removeAllPrefixedSharedWaypoints(Object waypointSet) {
		boolean changed = false;
		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return false;
			}

			List<Object> toRemove = new ArrayList<>();
			for (Object waypointObject : waypoints) {
				if (waypointObject == null) {
					continue;
				}
				String name = stringValue(invokeNoArg(waypointObject, "getName"));
				if (name != null && name.startsWith(SHARED_PREFIX)) {
					toRemove.add(waypointObject);
				}
			}

			for (Object waypointObject : toRemove) {
				invokeSingleArg(waypointSet, "remove", waypointObject);
				changed = true;
			}
		} catch (Exception e) {
			LOGGER.debug("Failed to remove prefixed shared waypoints: {}", e.getMessage());
		}
		appliedRemoteWaypointObjects.clear();
		trackedEntityWaypointLastPositions.clear();
		return changed;
	}

	private static Vec3d resolveWaypointSyncPosition(SharedWaypointInfo waypoint, String currentDimension) {
		if (waypoint == null) {
			return null;
		}
		String targetType = waypoint.targetType();
		String targetEntityId = waypoint.targetEntityId();
		if (!"entity".equalsIgnoreCase(targetType) || targetEntityId == null || targetEntityId.isBlank()) {
			return new Vec3d(waypoint.x() + 0.5D, waypoint.y(), waypoint.z() + 0.5D);
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null) {
			String worldDimension = client.world.getRegistryKey().getValue().toString();
			if (Objects.equals(currentDimension, worldDimension)) {
				for (Entity entity : client.world.getEntities()) {
					if (entity != null && targetEntityId.equals(entity.getUuidAsString())) {
						Vec3d pos = entity.getPos();
						trackedEntityWaypointLastPositions.put(waypoint.waypointId(), pos);
						return pos;
					}
				}

				if (isPlayerTarget(waypoint)) {
					Vec3d localPlayerPos = resolveLocalPlayerPositionFallback(client, targetEntityId, waypoint.targetEntityName());
					if (localPlayerPos != null) {
						trackedEntityWaypointLastPositions.put(waypoint.waypointId(), localPlayerPos);
						return localPlayerPos;
					}
				}
			}
		}

		if (boundNetworkManager != null) {
			Vec3d remotePos = boundNetworkManager.getRemoteEntityPosition(targetEntityId, currentDimension);
			if (remotePos != null) {
				trackedEntityWaypointLastPositions.put(waypoint.waypointId(), remotePos);
				return remotePos;
			}

			if (isPlayerTarget(waypoint)) {
				Vec3d remotePlayerPos = boundNetworkManager.getRemotePlayerPosition(targetEntityId, waypoint.targetEntityName(), currentDimension);
				if (remotePlayerPos != null) {
					trackedEntityWaypointLastPositions.put(waypoint.waypointId(), remotePlayerPos);
					return remotePlayerPos;
				}
			}
		}

		Vec3d lastKnown = trackedEntityWaypointLastPositions.get(waypoint.waypointId());
		if (lastKnown != null) {
			return lastKnown;
		}

		Vec3d initial = new Vec3d(waypoint.x() + 0.5D, waypoint.y(), waypoint.z() + 0.5D);
		trackedEntityWaypointLastPositions.put(waypoint.waypointId(), initial);
		return initial;
	}

	private static Vec3d resolveLocalPlayerPositionFallback(MinecraftClient client, String targetEntityId, String targetEntityName) {
		if (client == null || client.world == null) {
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
				String currentName = player.getName().getString();
				if (currentName != null && currentName.equalsIgnoreCase(targetEntityName)) {
					return player.getPos();
				}
			}
		}

		return null;
	}

	private static boolean isPlayerTarget(SharedWaypointInfo waypoint) {
		if (waypoint == null) {
			return false;
		}
		String targetEntityType = waypoint.targetEntityType();
		return targetEntityType != null && "minecraft:player".equalsIgnoreCase(targetEntityType);
	}

	private static SharedWaypointInfo copyWaypointWithPosition(SharedWaypointInfo source, int x, int y, int z) {
		return new SharedWaypointInfo(
				source.waypointId(),
				source.ownerId(),
				source.ownerName(),
				source.name(),
				source.symbol(),
				x,
				y,
				z,
				source.dimension(),
				source.color(),
				source.createdAt(),
				source.targetType(),
				source.targetEntityId(),
				source.targetEntityType(),
				source.targetEntityName(),
				source.waypointKind());
	}

	private static boolean waypointPositionChanged(Object waypointObject, int expectedX, int expectedY, int expectedZ) {
		try {
			int currentX = intValue(invokeNoArg(waypointObject, "getX"), expectedX);
			int currentY = intValue(invokeNoArg(waypointObject, "getY"), expectedY);
			int currentZ = intValue(invokeNoArg(waypointObject, "getZ"), expectedZ);
			return currentX != expectedX || currentY != expectedY || currentZ != expectedZ;
		} catch (Exception e) {
			return true;
		}
	}

	private static void saveWaypointWorld(Object minimapSession, Object currentWorld) {
		try {
			Object waypointSession = invokeNoArg(minimapSession, "getWaypointSession");
			if (waypointSession != null) {
				invokeSingleArg(waypointSession, "setSetChangedTime", System.currentTimeMillis());
			}
			Object worldManagerIO = invokeNoArg(minimapSession, "getWorldManagerIO");
			if (worldManagerIO != null) {
				invokeSingleArg(worldManagerIO, "saveWorld", currentWorld);
			}
		} catch (Exception e) {
			LOGGER.debug("Failed to save waypoint world: {}", e.getMessage());
		}
	}

	private static Object createXaeroWaypoint(String decoratedName, SharedWaypointInfo sharedWaypoint) {
		try {
			Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
			Constructor<?> constructor = waypointClass.getConstructor(
					int.class,
					int.class,
					int.class,
					String.class,
					String.class,
					int.class);
			Object waypoint = constructor.newInstance(
					sharedWaypoint.x(),
					sharedWaypoint.y(),
					sharedWaypoint.z(),
					decoratedName,
					safeSymbol(sharedWaypoint.symbol()),
					sharedWaypoint.color());

			try {
				Method includeY = waypointClass.getMethod("setYIncluded", boolean.class);
				includeY.invoke(waypoint, true);
			} catch (Exception ignored) {
			}

			return waypoint;
		} catch (Exception e) {
			LOGGER.error("Failed to create Xaero waypoint object: {}", e.getMessage());
			return null;
		}
	}

	private static boolean containsWaypointObject(Object waypointSet, Object targetObject) {
		if (targetObject == null || waypointSet == null) {
			return false;
		}
		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return false;
			}
			for (Object waypoint : waypoints) {
				if (waypoint == targetObject) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private static Object getMinimapSession() {
		try {
			Class<?> xaeroSessionClass = Class.forName("xaero.common.XaeroMinimapSession");
			Method getCurrentSession = xaeroSessionClass.getMethod("getCurrentSession");
			Object xaeroSession = getCurrentSession.invoke(null);
			if (xaeroSession == null) {
				return null;
			}
			Object minimapProcessor = invokeNoArg(xaeroSession, "getMinimapProcessor");
			if (minimapProcessor == null) {
				return null;
			}
			return invokeNoArg(minimapProcessor, "getSession");
		} catch (Exception e) {
			return null;
		}
	}

	private static Object invokeNoArg(Object target, String methodName) throws Exception {
		if (target == null) {
			return null;
		}
		Method method = target.getClass().getMethod(methodName);
		return method.invoke(target);
	}

	private static void invokeSingleArg(Object target, String methodName, Object arg) throws Exception {
		if (target == null) {
			return;
		}
		Method[] methods = target.getClass().getMethods();
		for (Method method : methods) {
			if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> parameterType = method.getParameterTypes()[0];
			if (isArgumentCompatible(parameterType, arg)) {
				method.invoke(target, arg);
				return;
			}
		}
		throw new NoSuchMethodException("No matching method: " + methodName + "(" + arg + ")");
	}

	private static boolean isArgumentCompatible(Class<?> parameterType, Object arg) {
		if (arg == null) {
			return !parameterType.isPrimitive();
		}
		if (parameterType.isPrimitive()) {
			return (parameterType == long.class && arg instanceof Long)
					|| (parameterType == int.class && arg instanceof Integer)
					|| (parameterType == boolean.class && arg instanceof Boolean)
					|| (parameterType == double.class && arg instanceof Double)
					|| (parameterType == float.class && arg instanceof Float);
		}
		return parameterType.isAssignableFrom(arg.getClass());
	}

	private static String decorateSharedName(SharedWaypointInfo sharedWaypoint) {
		String ownerName = sharedWaypoint.ownerName() == null || sharedWaypoint.ownerName().isBlank()
				? "Unknown"
				: sharedWaypoint.ownerName();
		String name = compactSharedWaypointName(sharedWaypoint);
		return SHARED_PREFIX + ownerName + ": " + name;
	}

	private static String compactSharedWaypointName(SharedWaypointInfo sharedWaypoint) {
		if (sharedWaypoint == null) {
			return "Waypoint";
		}

		String rawName = sharedWaypoint.name();
		String baseName = (rawName == null || rawName.isBlank()) ? "Waypoint" : rawName.trim();

		if ("quick".equalsIgnoreCase(sharedWaypoint.waypointKind())) {
			if ("entity".equalsIgnoreCase(sharedWaypoint.targetType())) {
				String entityName = sharedWaypoint.targetEntityName();
				if (entityName != null && !entityName.isBlank()) {
					return "报点[实体] " + entityName.trim();
				}
				return "报点[实体]";
			}
			return "报点";
		}

		String nameWithoutAt = baseName.replaceFirst("\\s*@\\s*-?\\d+\\s+-?\\d+\\s+-?\\d+\\s*$", "").trim();
		if (!nameWithoutAt.isBlank()) {
			baseName = nameWithoutAt;
		}

		String nameWithoutTrailingCoords = baseName.replaceFirst("\\s+-?\\d+\\s+-?\\d+\\s+-?\\d+\\s*$", "").trim();
		if (!nameWithoutTrailingCoords.isBlank()) {
			baseName = nameWithoutTrailingCoords;
		}

		return baseName;
	}

	private static int intValue(Object value, int fallback) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		return fallback;
	}

	private static long longValue(Object value, long fallback) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return fallback;
	}

	private static String stringValue(Object value) {
		return value == null ? null : value.toString();
	}

	private static String safeSymbol(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			return "W";
		}
		String trimmed = symbol.trim();
		return trimmed.length() > 2 ? trimmed.substring(0, 2) : trimmed;
	}
}
