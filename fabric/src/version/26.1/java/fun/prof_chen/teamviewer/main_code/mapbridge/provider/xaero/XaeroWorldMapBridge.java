package fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero;

import fun.prof_chen.teamviewer.main_code.bridge.MinecraftDimensionAdapter;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reflection-only Xaero adapter, isolated from common and Xaero compile-time APIs. */
public final class XaeroWorldMapBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(XaeroWorldMapBridge.class);
    private static final String TRACKER_ID = "teamviewer_remote_players";
    private static volatile Map<UUID, RemotePlayerInfo> remotePlayers = Collections.emptyMap();
    private static volatile boolean enabled;
    private static volatile boolean registered;
    private static volatile boolean disabled;
    private static volatile long lastAttempt;

    private XaeroWorldMapBridge() {
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("xaeroworldmap") && !disabled;
    }

    public static void tick(Map<UUID, RemotePlayerInfo> source, boolean relayEnabled) {
        remotePlayers = source == null || source.isEmpty() ? Collections.emptyMap() : Map.copyOf(source);
        enabled = relayEnabled;
        if (!isAvailable() || registered || System.currentTimeMillis() - lastAttempt < 2_000L) {
            return;
        }
        lastAttempt = System.currentTimeMillis();
        tryRegister();
    }

    private static void tryRegister() {
        try {
            ClassLoader loader = XaeroWorldMapBridge.class.getClassLoader();
            Class<?> worldMap = Class.forName("xaero.map.WorldMap", true, loader);
            Object manager = worldMap.getField("playerTrackerSystemManager").get(null);
            if (manager == null) {
                return;
            }
            Class<?> systemType = Class.forName("xaero.map.radar.tracker.system.IPlayerTrackerSystem", true, loader);
            Class<?> readerType = Class.forName("xaero.map.radar.tracker.system.ITrackedPlayerReader", true, loader);
            Object reader = Proxy.newProxyInstance(loader, new Class<?>[]{readerType}, new ReaderInvocation());
            Object system = Proxy.newProxyInstance(loader, new Class<?>[]{systemType}, new SystemInvocation(reader));
            manager.getClass().getMethod("register", String.class, systemType).invoke(manager, TRACKER_ID, system);
            registered = true;
            LOGGER.info("Xaero World Map bridge registered: {}", TRACKER_ID);
        } catch (ClassNotFoundException exception) {
            disabled = true;
            LOGGER.warn("Xaero World Map API was not found; integration disabled");
        } catch (ReflectiveOperationException exception) {
            LOGGER.error("Failed to register Xaero World Map bridge: {}", exception.getMessage());
        }
    }

    private static Iterator<RemotePlayerInfo> trackedPlayers() {
        if (!enabled) {
            return Collections.emptyIterator();
        }
        Minecraft client = Minecraft.getInstance();
        UUID localId = client.player == null ? null : client.player.getUUID();
        String dimension = client.level == null ? null
                : MinecraftDimensionAdapter.toDimensionId(client.level.dimension());
        List<RemotePlayerInfo> result = new ArrayList<>();
        for (RemotePlayerInfo player : remotePlayers.values()) {
            if (player == null || (localId != null && localId.equals(player.uuid()))) {
                continue;
            }
            if (dimension != null && player.dimension() != null && !dimension.equals(player.dimension())) {
                continue;
            }
            result.add(player);
        }
        return result.iterator();
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> null;
        };
    }

    private record SystemInvocation(Object reader) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "getReader" -> reader;
                case "getTrackedPlayerIterator" -> trackedPlayers();
                default -> null;
            };
        }
    }

    private static final class ReaderInvocation implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if (args == null || args.length == 0 || !(args[0] instanceof RemotePlayerInfo player)) {
                return null;
            }
            return switch (method.getName()) {
                case "getId" -> player.uuid();
                case "getX" -> player.position().x();
                case "getY" -> player.position().y();
                case "getZ" -> player.position().z();
                case "getDimension" -> MinecraftDimensionAdapter.toResourceKey(player.dimension(), Level.OVERWORLD);
                default -> null;
            };
        }
    }
}
