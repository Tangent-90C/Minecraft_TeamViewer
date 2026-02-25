package person.professor_chen.teamviewer.multipleplayeresp.bridge;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import person.professor_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
 
public final class XaeroWorldMapBridge {
	private static final Logger LOGGER = LoggerFactory.getLogger(XaeroWorldMapBridge.class);
	private static final String XAERO_MOD_ID = "xaeroworldmap";
	private static final String TRACKER_SYSTEM_ID = "teamviewer_remote_players";

	private static volatile boolean registered = false;
	private static volatile boolean disabled = false;
	private static volatile long lastAttemptMs = 0L;
	private static volatile Map<UUID, RemotePlayerInfo> remotePlayers = Collections.emptyMap();
	private static volatile boolean espEnabled = false;

	private XaeroWorldMapBridge() {
	}

	public static void tick(Map<UUID, RemotePlayerInfo> remotePlayersSource, boolean enabled) {
		if (remotePlayersSource != null) {
			remotePlayers = remotePlayersSource.isEmpty() ? Collections.emptyMap() : Map.copyOf(remotePlayersSource);
		}
		espEnabled = enabled;

		if (disabled || registered || !FabricLoader.getInstance().isModLoaded(XAERO_MOD_ID)) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastAttemptMs < 2_000L) {
			return;
		}
		lastAttemptMs = now;

		tryRegister();
	}

	private static void tryRegister() {
		try {
			ClassLoader cl = XaeroWorldMapBridge.class.getClassLoader();
			Class<?> worldMapClass = Class.forName("xaero.map.WorldMap", true, cl);
			Object manager = worldMapClass.getField("playerTrackerSystemManager").get(null);
			if (manager == null) {
				return;
			}

			Class<?> trackerSystemClass = Class.forName("xaero.map.radar.tracker.system.IPlayerTrackerSystem", true,
					cl);
			Class<?> readerClass = Class.forName("xaero.map.radar.tracker.system.ITrackedPlayerReader", true, cl);

			Object readerProxy = Proxy.newProxyInstance(cl, new Class<?>[] { readerClass },
					new TrackedPlayerReaderInvocation());
			Object trackerSystemProxy = Proxy.newProxyInstance(cl, new Class<?>[] { trackerSystemClass },
					new TrackerSystemInvocation(readerProxy));

			Method registerMethod = manager.getClass().getMethod("register", String.class, trackerSystemClass);
			registerMethod.invoke(manager, TRACKER_SYSTEM_ID, trackerSystemProxy);

			registered = true;
			LOGGER.info("Xaero World Map bridge registered: {}", TRACKER_SYSTEM_ID);
		} catch (ClassNotFoundException e) {
			disabled = true;
			LOGGER.warn("Xaero classes not found, bridge disabled.");
		} catch (Exception e) {
			LOGGER.error("Failed to register Xaero World Map bridge: {}", e.getMessage());
		}
	}

	private static Iterator<RemotePlayerInfo> buildTrackedPlayerIterator() {
		if (!espEnabled) {
			return Collections.emptyIterator();
		}

		MinecraftClient client = MinecraftClient.getInstance();
		UUID localPlayerId = client.player != null ? client.player.getUuid() : null;
		RegistryKey<World> currentDimension = client.world != null ? client.world.getRegistryKey() : null;

		List<RemotePlayerInfo> snapshot = new ArrayList<>();
		for (RemotePlayerInfo info : remotePlayers.values()) {
			if (info == null) {
				continue;
			}

			if (localPlayerId != null && localPlayerId.equals(info.uuid())) {
				continue;
			}

			if (currentDimension != null && info.dimension() != null && !currentDimension.equals(info.dimension())) {
				continue;
			}

			snapshot.add(info);
		}

		return snapshot.iterator();
	}

	private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
		String name = method.getName();
		if ("toString".equals(name)) {
			return proxy.getClass().getName();
		}
		if ("hashCode".equals(name)) {
			return System.identityHashCode(proxy);
		}
		if ("equals".equals(name)) {
			return proxy == (args == null ? null : args[0]);
		}
		return null;
	}

	private static final class TrackerSystemInvocation implements InvocationHandler {
		private final Object readerProxy;

		private TrackerSystemInvocation(Object readerProxy) {
			this.readerProxy = readerProxy;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (method.getDeclaringClass() == Object.class) {
				return handleObjectMethod(proxy, method, args);
			}

			return switch (method.getName()) {
				case "getReader" -> readerProxy;
				case "getTrackedPlayerIterator" -> buildTrackedPlayerIterator();
				default -> null;
			};
		}
	}

	private static final class TrackedPlayerReaderInvocation implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (method.getDeclaringClass() == Object.class) {
				return handleObjectMethod(proxy, method, args);
			}

			if (args == null || args.length == 0 || !(args[0] instanceof RemotePlayerInfo info)) {
				return null;
			}

			return switch (method.getName()) {
				case "getId" -> info.uuid();
				case "getX" -> info.position().x;
				case "getY" -> info.position().y;
				case "getZ" -> info.position().z;
				case "getDimension" -> info.dimension();
				default -> null;
			};
		}
	}
}