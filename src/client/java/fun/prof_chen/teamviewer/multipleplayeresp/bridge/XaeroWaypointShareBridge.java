package fun.prof_chen.teamviewer.multipleplayeresp.bridge;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fun.prof_chen.teamviewer.multipleplayeresp.config.Config;
import fun.prof_chen.teamviewer.multipleplayeresp.model.ReportDataSchemas;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.network.PlayerESPNetworkManager;

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
	private static final Map<String, String> appliedRemoteWaypointNames = new ConcurrentHashMap<>();
	private static final Map<String, String> appliedRemoteWaypointFingerprints = new ConcurrentHashMap<>();
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
		appliedRemoteWaypointNames.remove(waypointId);
		appliedRemoteWaypointFingerprints.remove(waypointId);
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
			syncDeletedSharedWaypointsFromMinimap(networkManager, ownerId, dimension, config);

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
					Map<String, Object> payload = new ReportDataSchemas.WaypointDataPayload(
							waypoint.x(),
							waypoint.y(),
							waypoint.z(),
							waypoint.dimension(),
							waypoint.name(),
							waypoint.symbol(),
							waypoint.color(),
							ownerId.toString(),
							ownerName,
							waypoint.createdAt(),
							manualTtl,
							"manual",
							null,
							null,
							null,
							null,
							null,
							null,
							null,
							null,
							null,
							null).toMap();
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

	private static void syncDeletedSharedWaypointsFromMinimap(
			PlayerESPNetworkManager networkManager,
			UUID localPlayerId,
			String currentDimension,
			Config config) {
		if (networkManager == null || localPlayerId == null || currentDimension == null || config == null) {
			return;
		}
		if (!config.isShowSharedWaypoints()) {
			return;
		}

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

			boolean includeOwnSharedWaypoints = config.isShowOwnSharedWaypointsOnMinimap();
			List<String> deletedIds = new ArrayList<>();
			boolean removedDemotedWaypoint = false;

			for (String waypointId : new ArrayList<>(appliedRemoteWaypointNames.keySet())) {
				if (waypointId == null || waypointId.isBlank() || pendingRemoteDeletes.contains(waypointId)) {
					continue;
				}

				SharedWaypointInfo waypoint = latestRemoteWaypoints.get(waypointId);
				if (!shouldTrackSharedWaypointDeletion(waypoint, localPlayerId, currentDimension, includeOwnSharedWaypoints)) {
					continue;
				}

				Object existingObject = appliedRemoteWaypointObjects.get(waypointId);
				if (existingObject != null && containsWaypointObject(currentWaypointSet, existingObject)) {
					continue;
				}

				String decoratedName = appliedRemoteWaypointNames.get(waypointId);
				if (decoratedName == null || decoratedName.isBlank()) {
					continue;
				}

				Object currentObject = findWaypointObjectByExactName(currentWaypointSet, decoratedName);
				if (currentObject != null) {
					appliedRemoteWaypointObjects.put(waypointId, currentObject);
					continue;
				}

				Object demotedObject = findDemotedSharedWaypointObject(currentWaypointSet, waypoint, currentDimension);
				if (demotedObject != null) {
					invokeSingleArg(currentWaypointSet, "remove", demotedObject);
					removedDemotedWaypoint = true;
				}

				deletedIds.add(waypointId);
			}

			if (removedDemotedWaypoint) {
				saveWaypointWorld(minimapSession, currentWorld);
			}

			if (!deletedIds.isEmpty()) {
				deleteSharedWaypoints(deletedIds);
				networkManager.sendWaypointsDelete(localPlayerId, deletedIds);
			}
		} catch (Exception e) {
			LOGGER.debug("Failed to detect shared waypoint deletions from minimap: {}", e.getMessage());
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
			Map<String, List<String>> fingerprintToIds = new HashMap<>();

			if (remoteReconcileRequired) {
				changed |= removeAllSharedWaypoints(currentWaypointSet);
				appliedRemoteWaypointObjects.clear();
				appliedRemoteWaypointNames.clear();
				appliedRemoteWaypointFingerprints.clear();
				remoteReconcileRequired = false;
			}

			for (Map.Entry<String, SharedWaypointInfo> entry : latestRemoteWaypoints.entrySet()) {
				SharedWaypointInfo waypoint = entry.getValue();
				if (waypoint == null) {
					continue;
				}
				if (shouldSuppressRemoteWaypointOnMinimap(waypoint, localPlayerId, currentDimension, includeOwnSharedWaypoints)) {
					continue;
				}
				if (!includeOwnSharedWaypoints && waypoint.ownerId() != null && waypoint.ownerId().equals(localPlayerId)) {
					continue;
				}
				if (waypoint.dimension() != null && !waypoint.dimension().isBlank()
						&& !Objects.equals(waypoint.dimension(), currentDimension)) {
					continue;
				}
				Vec3d trackedPos = resolveWaypointSyncPosition(waypoint, currentDimension);
				SharedWaypointInfo renderWaypoint = waypoint;
				if (trackedPos != null) {
					renderWaypoint = copyWaypointWithPosition(waypoint,
							(int) Math.floor(trackedPos.x),
							(int) Math.floor(trackedPos.y),
							(int) Math.floor(trackedPos.z));
				}
				String fingerprint = renderFingerprint(renderWaypoint);
				fingerprintToIds.computeIfAbsent(fingerprint, ignored -> new ArrayList<>()).add(entry.getKey());
			}

			changed |= reconcileExistingSharedWaypointsByFingerprint(currentWaypointSet, fingerprintToIds);

			for (Map.Entry<String, SharedWaypointInfo> entry : latestRemoteWaypoints.entrySet()) {
				String waypointId = entry.getKey();
				SharedWaypointInfo waypoint = entry.getValue();
				if (waypoint == null) {
					continue;
				}
				if (shouldSuppressRemoteWaypointOnMinimap(waypoint, localPlayerId, currentDimension, includeOwnSharedWaypoints)) {
					removeRemoteWaypointById(currentWaypointSet, waypointId);
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
				String fingerprint = renderFingerprint(renderWaypoint);

				Object existingObject = appliedRemoteWaypointObjects.get(waypointId);
				if (existingObject != null && containsWaypointObject(currentWaypointSet, existingObject)) {
					if (!waypointRenderChanged(existingObject, renderWaypoint, fingerprint)) {
						appliedRemoteWaypointNames.put(waypointId, decoratedName);
						appliedRemoteWaypointFingerprints.put(waypointId, fingerprint);
						continue;
					}
					removeRemoteWaypointById(currentWaypointSet, waypointId);
					changed = true;
				} else {
					appliedRemoteWaypointObjects.remove(waypointId);
					appliedRemoteWaypointNames.remove(waypointId);
					appliedRemoteWaypointFingerprints.remove(waypointId);
					Object matchedObject = findWaypointObjectByFingerprint(currentWaypointSet, fingerprint, null);
					if (matchedObject != null) {
						appliedRemoteWaypointObjects.put(waypointId, matchedObject);
						appliedRemoteWaypointNames.put(waypointId, decoratedName);
						appliedRemoteWaypointFingerprints.put(waypointId, fingerprint);
						continue;
					}
				}

				Object newWaypoint = createXaeroWaypoint(decoratedName, renderWaypoint);
				if (newWaypoint == null) {
					continue;
				}
				invokeSingleArg(currentWaypointSet, "add", newWaypoint);
				appliedRemoteWaypointObjects.put(waypointId, newWaypoint);
				appliedRemoteWaypointNames.put(waypointId, decoratedName);
				appliedRemoteWaypointFingerprints.put(waypointId, fingerprint);
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

	private static boolean reconcileExistingSharedWaypointsByFingerprint(Object waypointSet, Map<String, List<String>> fingerprintToIds) {
		boolean changed = false;
		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return false;
			}

			List<Object> staleObjects = new ArrayList<>();
			for (Object waypointObject : waypoints) {
				if (waypointObject == null) {
					continue;
				}
				String name = stringValue(invokeNoArg(waypointObject, "getName"));
				if (name == null || !name.startsWith(SHARED_PREFIX)) {
					continue;
				}

				String fingerprint = renderFingerprint(waypointObject);
				List<String> matchedIds = fingerprintToIds.get(fingerprint);
				if (matchedIds == null || matchedIds.isEmpty()) {
					staleObjects.add(waypointObject);
					continue;
				}

				String matchedId = matchedIds.remove(0);
				appliedRemoteWaypointObjects.put(matchedId, waypointObject);
				appliedRemoteWaypointNames.put(matchedId, name);
				appliedRemoteWaypointFingerprints.put(matchedId, fingerprint);
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
					"manual",
					null,
					null));
		}

		return result;
	}

	private static boolean removeRemoteWaypointById(Object waypointSet, String waypointId) {
		Object existingObject = appliedRemoteWaypointObjects.remove(waypointId);
		appliedRemoteWaypointNames.remove(waypointId);
		String fingerprint = appliedRemoteWaypointFingerprints.remove(waypointId);
		if (existingObject == null && fingerprint != null) {
			existingObject = findWaypointObjectByFingerprint(waypointSet, fingerprint, null);
		}
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
		appliedRemoteWaypointNames.clear();
		appliedRemoteWaypointFingerprints.clear();
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
				source.waypointKind(),
				source.tacticalType(),
				source.sourceType());
	}

	private static boolean waypointRenderChanged(Object waypointObject, SharedWaypointInfo expectedWaypoint, String expectedFingerprint) {
		try {
			String currentFingerprint = renderFingerprint(waypointObject);
			if (!Objects.equals(currentFingerprint, expectedFingerprint)) {
				return true;
			}
			return waypointPositionChanged(waypointObject, expectedWaypoint.x(), expectedWaypoint.y(), expectedWaypoint.z());
		} catch (Exception e) {
			return true;
		}
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

	private static Object findWaypointObjectByExactName(Object waypointSet, String exactName) {
		if (waypointSet == null || exactName == null || exactName.isBlank()) {
			return null;
		}
		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return null;
			}
			for (Object waypointObject : waypoints) {
				if (waypointObject == null) {
					continue;
				}
				String name = stringValue(invokeNoArg(waypointObject, "getName"));
				if (exactName.equals(name)) {
					return waypointObject;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static Object findWaypointObjectByFingerprint(Object waypointSet, String fingerprint, Object excludeObject) {
		if (waypointSet == null || fingerprint == null || fingerprint.isBlank()) {
			return null;
		}
		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return null;
			}
			for (Object waypointObject : waypoints) {
				if (waypointObject == null || waypointObject == excludeObject) {
					continue;
				}
				String name = stringValue(invokeNoArg(waypointObject, "getName"));
				if (name == null || !name.startsWith(SHARED_PREFIX)) {
					continue;
				}
				if (fingerprint.equals(renderFingerprint(waypointObject))) {
					return waypointObject;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static Object findDemotedSharedWaypointObject(Object waypointSet, SharedWaypointInfo waypoint, String currentDimension) {
		if (waypointSet == null || waypoint == null) {
			return null;
		}

		Set<String> candidateNames = new HashSet<>();
		String compactName = compactSharedWaypointName(waypoint);
		if (compactName != null && !compactName.isBlank()) {
			candidateNames.add(compactName.trim());
		}
		String rawName = waypoint.name();
		if (rawName != null && !rawName.isBlank()) {
			candidateNames.add(rawName.trim());
		}
		if (candidateNames.isEmpty()) {
			return null;
		}

		Vec3d resolvedPos = resolveWaypointSyncPosition(waypoint, currentDimension);
		int expectedX = resolvedPos != null ? (int) Math.floor(resolvedPos.x) : waypoint.x();
		int expectedY = resolvedPos != null ? (int) Math.floor(resolvedPos.y) : waypoint.y();
		int expectedZ = resolvedPos != null ? (int) Math.floor(resolvedPos.z) : waypoint.z();

		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return null;
			}
			for (Object waypointObject : waypoints) {
				if (waypointObject == null) {
					continue;
				}
				String name = stringValue(invokeNoArg(waypointObject, "getName"));
				if (name == null || name.isBlank() || name.startsWith(SHARED_PREFIX)) {
					continue;
				}
				if (!candidateNames.contains(name.trim())) {
					continue;
				}

				int waypointX = intValue(invokeNoArg(waypointObject, "getX"), expectedX);
				int waypointY = intValue(invokeNoArg(waypointObject, "getY"), expectedY);
				int waypointZ = intValue(invokeNoArg(waypointObject, "getZ"), expectedZ);
				if (waypointX == expectedX && waypointY == expectedY && waypointZ == expectedZ) {
					return waypointObject;
				}
			}
		} catch (Exception ignored) {
		}

		return null;
	}

	private static boolean shouldTrackSharedWaypointDeletion(
			SharedWaypointInfo waypoint,
			UUID localPlayerId,
			String currentDimension,
			boolean includeOwnSharedWaypoints) {
		if (waypoint == null) {
			return false;
		}
		if (!includeOwnSharedWaypoints && waypoint.ownerId() != null && waypoint.ownerId().equals(localPlayerId)) {
			return false;
		}
		return waypoint.dimension() == null
				|| waypoint.dimension().isBlank()
				|| Objects.equals(waypoint.dimension(), currentDimension);
	}

	private static boolean shouldSuppressRemoteWaypointOnMinimap(
			SharedWaypointInfo waypoint,
			UUID localPlayerId,
			String currentDimension,
			boolean includeOwnSharedWaypoints) {
		if (waypoint == null) {
			return false;
		}
		if (!includeOwnSharedWaypoints) {
			return waypoint.ownerId() != null && waypoint.ownerId().equals(localPlayerId);
		}
		if (waypoint.ownerId() == null || !waypoint.ownerId().equals(localPlayerId)) {
			return false;
		}
		if (waypoint.dimension() != null && !waypoint.dimension().isBlank()
				&& !Objects.equals(waypoint.dimension(), currentDimension)) {
			return false;
		}
		String waypointId = waypoint.waypointId();
		return waypointId != null && knownLocalWaypoints.containsKey(waypointId);
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
		String name = compactSharedWaypointName(sharedWaypoint);
		if (isAdminTacticalWaypoint(sharedWaypoint)) {
			return SHARED_PREFIX + name;
		}

		String ownerName = sharedWaypoint.ownerName() == null || sharedWaypoint.ownerName().isBlank()
				? "Unknown"
				: sharedWaypoint.ownerName().trim();
		if (ownerName.equals(name)) {
			return SHARED_PREFIX + name;
		}
		return SHARED_PREFIX + ownerName + ": " + name;
	}

	private static String renderFingerprint(SharedWaypointInfo waypoint) {
		if (waypoint == null) {
			return "";
		}
		return decorateSharedName(waypoint)
				+ "|" + safeSymbol(waypoint.symbol())
				+ "|" + waypoint.color()
				+ "|" + waypoint.x()
				+ "|" + waypoint.y()
				+ "|" + waypoint.z();
	}

	private static String renderFingerprint(Object waypointObject) {
		if (waypointObject == null) {
			return "";
		}
		try {
			String name = stringValue(invokeNoArg(waypointObject, "getName"));
			String symbol = safeSymbol(stringValue(invokeNoArg(waypointObject, "getSymbol")));
			int color = intValue(invokeNoArg(waypointObject, "getColor"), 0);
			int x = intValue(invokeNoArg(waypointObject, "getX"), 0);
			int y = intValue(invokeNoArg(waypointObject, "getY"), 0);
			int z = intValue(invokeNoArg(waypointObject, "getZ"), 0);
			return (name == null ? "" : name)
					+ "|" + symbol
					+ "|" + color
					+ "|" + x
					+ "|" + y
					+ "|" + z;
		} catch (Exception e) {
			return "";
		}
	}

	private static boolean isAdminTacticalWaypoint(SharedWaypointInfo sharedWaypoint) {
		if (sharedWaypoint == null) {
			return false;
		}

		String sourceType = sharedWaypoint.sourceType();
		if (sourceType != null && sourceType.equalsIgnoreCase("admin_tactical")) {
			return true;
		}

		String waypointKind = sharedWaypoint.waypointKind();
		return waypointKind != null && waypointKind.equalsIgnoreCase("admin_tactical");
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
