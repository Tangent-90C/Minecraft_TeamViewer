package person.professor_chen.teamviewer.multipleplayeresp;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final long LOCAL_SCAN_INTERVAL_MS = 1_500L;
	private static final long REMOTE_APPLY_INTERVAL_MS = 1_000L;

	private static final Set<String> knownLocalWaypointKeys = ConcurrentHashMap.newKeySet();
	private static final Set<String> appliedRemoteWaypointIds = ConcurrentHashMap.newKeySet();
	private static final Map<String, SharedWaypointInfo> pendingRemoteWaypoints = new ConcurrentHashMap<>();

	private static volatile PlayerESPNetworkManager boundNetworkManager;
	private static volatile boolean listenerRegistered = false;
	private static volatile boolean baselineInitialized = false;
	private static volatile long lastLocalScanMs = 0L;
	private static volatile long lastRemoteApplyMs = 0L;

	private static final PlayerESPNetworkManager.WaypointUpdateListener WAYPOINT_LISTENER = waypoints -> {
		if (waypoints == null || waypoints.isEmpty()) {
			return;
		}
		for (Map.Entry<String, SharedWaypointInfo> entry : waypoints.entrySet()) {
			if (entry.getValue() != null) {
				pendingRemoteWaypoints.put(entry.getKey(), entry.getValue());
			}
		}
	};

	private XaeroWaypointShareBridge() {
	}

	public static void tick(PlayerESPNetworkManager networkManager, boolean espEnabled) {
		if (!espEnabled || networkManager == null) {
			return;
		}

		if (!FabricLoader.getInstance().isModLoaded(XAERO_MINIMAP_MOD_ID)) {
			return;
		}

		ensureWaypointListener(networkManager);

		long now = System.currentTimeMillis();
		if (networkManager.isConnected() && now - lastLocalScanMs >= LOCAL_SCAN_INTERVAL_MS) {
			lastLocalScanMs = now;
			scanAndUploadNewWaypoints(networkManager);
		}

		if (networkManager.isConnected() && now - lastRemoteApplyMs >= REMOTE_APPLY_INTERVAL_MS) {
			lastRemoteApplyMs = now;
			applyPendingRemoteWaypoints();
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

	private static void scanAndUploadNewWaypoints(PlayerESPNetworkManager networkManager) {
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) {
				return;
			}

			UUID ownerId = client.player.getUuid();
			String ownerName = client.player.getName().getString();
			String currentDimension = client.world.getRegistryKey().getValue().toString();

			List<SharedWaypointInfo> localWaypoints = readCurrentXaeroWaypoints(ownerId, ownerName, currentDimension);
			if (localWaypoints.isEmpty()) {
				return;
			}

			if (!baselineInitialized) {
				for (SharedWaypointInfo waypoint : localWaypoints) {
					knownLocalWaypointKeys.add(localWaypointFingerprint(waypoint));
				}
				baselineInitialized = true;
				return;
			}

			Map<String, Map<String, Object>> toSend = new HashMap<>();
			for (SharedWaypointInfo waypoint : localWaypoints) {
				String localKey = localWaypointFingerprint(waypoint);
				if (!knownLocalWaypointKeys.add(localKey)) {
					continue;
				}

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
				toSend.put(waypoint.waypointId(), payload);
			}

			if (!toSend.isEmpty()) {
				networkManager.sendWaypointsUpdate(ownerId, toSend);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to scan/upload shared waypoints: {}", e.getMessage());
		}
	}

	private static void applyPendingRemoteWaypoints() {
		if (pendingRemoteWaypoints.isEmpty()) {
			return;
		}

		try {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) {
				return;
			}

			String currentDimension = client.world.getRegistryKey().getValue().toString();
			UUID localPlayerId = client.player.getUuid();

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

			Set<String> existingKeys = collectExistingWaypointKeys(currentWaypointSet);
			int added = 0;

			for (SharedWaypointInfo sharedWaypoint : new ArrayList<>(pendingRemoteWaypoints.values())) {
				if (sharedWaypoint == null) {
					continue;
				}
				if (sharedWaypoint.ownerId() != null && sharedWaypoint.ownerId().equals(localPlayerId)) {
					pendingRemoteWaypoints.remove(sharedWaypoint.waypointId());
					continue;
				}

				if (sharedWaypoint.dimension() != null && !sharedWaypoint.dimension().isBlank()
						&& !Objects.equals(sharedWaypoint.dimension(), currentDimension)) {
					continue;
				}

				if (appliedRemoteWaypointIds.contains(sharedWaypoint.waypointId())) {
					pendingRemoteWaypoints.remove(sharedWaypoint.waypointId());
					continue;
				}

				String decoratedName = decorateSharedName(sharedWaypoint);
				String existingKey = existingWaypointFingerprint(decoratedName, sharedWaypoint.x(), sharedWaypoint.y(),
						sharedWaypoint.z());
				if (existingKeys.contains(existingKey)) {
					appliedRemoteWaypointIds.add(sharedWaypoint.waypointId());
					pendingRemoteWaypoints.remove(sharedWaypoint.waypointId());
					continue;
				}

				Object waypoint = createXaeroWaypoint(decoratedName, sharedWaypoint);
				if (waypoint == null) {
					continue;
				}

				invokeSingleArg(currentWaypointSet, "add", waypoint);
				existingKeys.add(existingKey);
				appliedRemoteWaypointIds.add(sharedWaypoint.waypointId());
				pendingRemoteWaypoints.remove(sharedWaypoint.waypointId());
				added++;
			}

			if (added > 0) {
				Object waypointSession = invokeNoArg(minimapSession, "getWaypointSession");
				if (waypointSession != null) {
					invokeSingleArg(waypointSession, "setSetChangedTime", System.currentTimeMillis());
				}

				Object worldManagerIO = invokeNoArg(minimapSession, "getWorldManagerIO");
				if (worldManagerIO != null) {
					invokeSingleArg(worldManagerIO, "saveWorld", currentWorld);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to apply remote shared waypoints: {}", e.getMessage());
		}
	}

	private static List<SharedWaypointInfo> readCurrentXaeroWaypoints(UUID ownerId, String ownerName, String dimension)
			throws Exception {
		Object minimapSession = getMinimapSession();
		if (minimapSession == null) {
			return List.of();
		}

		Object worldManager = invokeNoArg(minimapSession, "getWorldManager");
		Object currentWorld = invokeNoArg(worldManager, "getCurrentWorld");
		if (currentWorld == null) {
			return List.of();
		}

		Object currentWaypointSet = invokeNoArg(currentWorld, "getCurrentWaypointSet");
		if (currentWaypointSet == null) {
			return List.of();
		}

		Object iterableObject = invokeNoArg(currentWaypointSet, "getWaypoints");
		if (!(iterableObject instanceof Iterable<?> waypoints)) {
			return List.of();
		}

		List<SharedWaypointInfo> result = new ArrayList<>();
		for (Object waypoint : waypoints) {
			if (waypoint == null) {
				continue;
			}
			String name = stringValue(invokeNoArg(waypoint, "getName"));
			if (name == null || name.isBlank() || name.startsWith("[TV]")) {
				continue;
			}

			int x = intValue(invokeNoArg(waypoint, "getX"), 0);
			int y = intValue(invokeNoArg(waypoint, "getY"), 64);
			int z = intValue(invokeNoArg(waypoint, "getZ"), 0);
			int color = intValue(invokeNoArg(waypoint, "getColor"), 0x55FF55);
			String symbol = stringValue(invokeNoArg(waypoint, "getSymbol"));

			String idSource = ownerId + "|" + dimension + "|" + x + "|" + y + "|" + z + "|" + name + "|"
					+ safeSymbol(symbol);
			String waypointId = UUID.nameUUIDFromBytes(idSource.getBytes(StandardCharsets.UTF_8)).toString();

			result.add(new SharedWaypointInfo(
					waypointId,
					ownerId,
					ownerName,
					name,
					safeSymbol(symbol),
					x,
					y,
					z,
					dimension,
					color,
					System.currentTimeMillis()));
		}

		return result;
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

	private static Set<String> collectExistingWaypointKeys(Object waypointSet) {
		Set<String> keys = new HashSet<>();
		try {
			Object iterableObject = invokeNoArg(waypointSet, "getWaypoints");
			if (!(iterableObject instanceof Iterable<?> waypoints)) {
				return keys;
			}

			for (Object waypoint : waypoints) {
				if (waypoint == null) {
					continue;
				}
				String name = stringValue(invokeNoArg(waypoint, "getName"));
				int x = intValue(invokeNoArg(waypoint, "getX"), 0);
				int y = intValue(invokeNoArg(waypoint, "getY"), 64);
				int z = intValue(invokeNoArg(waypoint, "getZ"), 0);
				keys.add(existingWaypointFingerprint(name, x, y, z));
			}
		} catch (Exception e) {
			LOGGER.error("Failed to collect existing waypoint keys: {}", e.getMessage());
		}
		return keys;
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
		String name = sharedWaypoint.name() == null || sharedWaypoint.name().isBlank() ? "Waypoint" : sharedWaypoint.name();
		return "[TV] " + ownerName + ": " + name;
	}

	private static String localWaypointFingerprint(SharedWaypointInfo waypoint) {
		return waypoint.dimension() + "|" + waypoint.x() + "|" + waypoint.y() + "|" + waypoint.z() + "|"
				+ waypoint.name() + "|" + waypoint.symbol();
	}

	private static String existingWaypointFingerprint(String name, int x, int y, int z) {
		return (name == null ? "" : name) + "|" + x + "|" + y + "|" + z;
	}

	private static int intValue(Object value, int fallback) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		return fallback;
	}

	private static String stringValue(Object value) {
		if (value == null) {
			return null;
		}
		return value.toString();
	}

	private static String safeSymbol(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			return "W";
		}
		String trimmed = symbol.trim();
		return trimmed.length() > 2 ? trimmed.substring(0, 2) : trimmed;
	}
}