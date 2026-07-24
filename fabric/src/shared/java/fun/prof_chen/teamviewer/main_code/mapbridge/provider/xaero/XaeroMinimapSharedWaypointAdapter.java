package fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Cross-version reflection port for Xaero Minimap native waypoint CRUD. */
public final class XaeroMinimapSharedWaypointAdapter implements SharedWaypointMapAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(XaeroMinimapSharedWaypointAdapter.class);
    private final Map<String, Object> managed = new ConcurrentHashMap<>();

    @Override
    public String id() { return "xaero-minimap"; }

    @Override
    public boolean isAvailable() { return FabricLoader.getInstance().isModLoaded("xaerominimap"); }

    @Override
    public IntegrationSupportStatus supportStatus() {
        return isAvailable() ? IntegrationSupportStatus.AVAILABLE : IntegrationSupportStatus.MOD_NOT_INSTALLED;
    }

    @Override
    public List<NativeMapWaypointSnapshot> listLocalWaypoints() {
        List<NativeMapWaypointSnapshot> result = new ArrayList<>();
        withWaypointSet((session, world, set) -> {
            Object raw = invokeNoArg(set, "getWaypoints");
            if (!(raw instanceof Iterable<?> values)) return false;
            for (Object value : values) {
                if (value == null || managed.containsValue(value)) continue;
                String name = string(invokeNoArg(value, "getName"));
                if (name == null || name.isBlank() || name.startsWith("[TV] ")) continue;
                result.add(new NativeMapWaypointSnapshot(
                        string(invokeNoArg(value, "getId")), name, safeSymbol(string(invokeNoArg(value, "getSymbol"))),
                        integer(invokeNoArg(value, "getX"), 0), integer(invokeNoArg(value, "getY"), 64),
                        integer(invokeNoArg(value, "getZ"), 0), null,
                        integer(invokeNoArg(value, "getColor"), 0x55FF55)));
            }
            return false;
        });
        return result;
    }

    @Override
    public void upsertRemoteWaypoint(MapWaypointCommand command) {
        if (command == null) return;
        withWaypointSet((session, world, set) -> {
            Object existing = managed.get(command.waypointId());
            if (existing != null && same(existing, command)) return false;
            if (existing != null) invokeOneArg(set, "remove", existing);
            Object created = create(command);
            if (created == null) return existing != null;
            invokeOneArg(set, "add", created);
            managed.put(command.waypointId(), created);
            return true;
        });
    }

    @Override
    public void deleteRemoteWaypoint(String waypointId) {
        withWaypointSet((session, world, set) -> {
            Object value = managed.remove(waypointId);
            if (value == null) return false;
            invokeOneArg(set, "remove", value);
            return true;
        });
    }

    @Override
    public void clearRemoteWaypoints() {
        withWaypointSet((session, world, set) -> {
            boolean changed = false;
            for (Object value : new ArrayList<>(managed.values())) {
                invokeOneArg(set, "remove", value);
                changed = true;
            }
            managed.clear();
            return changed;
        });
    }

    private static Object create(MapWaypointCommand command) {
        try {
            Class<?> type = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Constructor<?> constructor = type.getConstructor(
                    int.class, int.class, int.class, String.class, String.class, int.class);
            Object value = constructor.newInstance(command.x(), command.y(), command.z(), command.name(),
                    safeSymbol(command.symbol()), command.color());
            try { type.getMethod("setYIncluded", boolean.class).invoke(value, true); } catch (Exception ignored) { }
            return value;
        } catch (Exception exception) {
            LOGGER.debug("Unable to create Xaero waypoint: {}", exception.getMessage());
            return null;
        }
    }

    private static boolean same(Object value, MapWaypointCommand command) {
        try {
            return Objects.equals(command.name(), string(invokeNoArg(value, "getName")))
                    && command.x() == integer(invokeNoArg(value, "getX"), Integer.MIN_VALUE)
                    && command.y() == integer(invokeNoArg(value, "getY"), Integer.MIN_VALUE)
                    && command.z() == integer(invokeNoArg(value, "getZ"), Integer.MIN_VALUE)
                    && command.color() == integer(invokeNoArg(value, "getColor"), Integer.MIN_VALUE);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void withWaypointSet(Action action) {
        if (!FabricLoader.getInstance().isModLoaded("xaerominimap")) return;
        try {
            Object session = currentSession();
            if (session == null) return;
            Object manager = invokeNoArg(session, "getWorldManager");
            Object world = invokeNoArg(manager, "getCurrentWorld");
            if (world == null) return;
            Object set = invokeNoArg(world, "getCurrentWaypointSet");
            if (set == null) return;
            if (action.apply(session, world, set)) save(session, world);
        } catch (Exception exception) {
            LOGGER.debug("Xaero waypoint port failed: {}", exception.getMessage());
        }
    }

    private static Object currentSession() {
        try {
            Class<?> type = Class.forName("xaero.common.XaeroMinimapSession");
            Object outer = type.getMethod("getCurrentSession").invoke(null);
            if (outer == null) return null;
            Object processor = invokeNoArg(outer, "getMinimapProcessor");
            return processor == null ? null : invokeNoArg(processor, "getSession");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void save(Object session, Object world) {
        try {
            Object io = invokeNoArg(session, "getWorldManagerIO");
            if (io != null) invokeOneArg(io, "saveWorld", world);
            try { invokeOneArg(session, "setSetChangedTime", System.currentTimeMillis()); } catch (Exception ignored) { }
        } catch (Exception ignored) { }
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (NoSuchMethodException exception) {
            if ("getId".equals(name)) return null;
            throw exception;
        }
    }

    private static void invokeOneArg(Object target, String name, Object argument) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            if (argument == null || type.isAssignableFrom(argument.getClass())
                    || (type == long.class && argument instanceof Long)) {
                method.invoke(target, argument);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static int integer(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static String safeSymbol(String value) {
        if (value == null || value.isBlank()) return "W";
        String trimmed = value.trim();
        return trimmed.length() > 2 ? trimmed.substring(0, 2) : trimmed;
    }

    @FunctionalInterface
    private interface Action { boolean apply(Object session, Object world, Object waypointSet) throws Exception; }
}
