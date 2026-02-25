package person.professor_chen.teamviewer.multipleplayeresp.bridge;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
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
	private static final Map<String, SharedWaypointInfo> pendingRemoteUpserts = new ConcurrentHashMap<>();
	private static final Set<String> pendingRemoteDeletes = ConcurrentHashMap.newKeySet();

	private static volatile PlayerESPNetworkManager boundNetworkManager;
	private static volatile boolean listenerRegistered = false;
	private static volatile boolean baselineInitialized = false;
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

	public static void tick(PlayerESPNetworkManager networkManager, boolean espEnabled, Config config) {
		if (!espEnabled || networkManager == null || config == null) {
			return;
		}
		if (!FabricLoader.getInstance().isModLoaded(XAERO_MINIMAP_MOD_ID)) {
			return;
		}

		ensureWaypointListener(networkManager);
		long now = System.currentTimeMillis();

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
				removeAllAppliedRemoteWaypoints(currentWaypointSet);
				saveWaypointWorld(minimapSession, currentWorld);
				return;
			}

			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) {
				return;
			}

			UUID localPlayerId = client.player.getUuid();
			String currentDimension = client.world.getRegistryKey().getValue().toString();
			boolean changed = false;
			Set<String> keepIds = new HashSet<>();

			for (Map.Entry<String, SharedWaypointInfo> entry : latestRemoteWaypoints.entrySet()) {
				String waypointId = entry.getKey();
				SharedWaypointInfo waypoint = entry.getValue();
				if (waypoint == null) {
					continue;
				}
				if (waypoint.ownerId() != null && waypoint.ownerId().equals(localPlayerId)) {
					continue;
				}
				if (waypoint.dimension() != null && !waypoint.dimension().isBlank()
						&& !Objects.equals(waypoint.dimension(), currentDimension)) {
					continue;
				}

				keepIds.add(waypointId);
				Object existingObject = appliedRemoteWaypointObjects.get(waypointId);
				if (existingObject != null && containsWaypointObject(currentWaypointSet, existingObject)) {
					continue;
				}

				Object newWaypoint = createXaeroWaypoint(decorateSharedName(waypoint), waypoint);
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
					createdAt));
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
		String name = sharedWaypoint.name() == null || sharedWaypoint.name().isBlank()
				? "Waypoint"
				: sharedWaypoint.name();
		return SHARED_PREFIX + ownerName + ": " + name;
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
