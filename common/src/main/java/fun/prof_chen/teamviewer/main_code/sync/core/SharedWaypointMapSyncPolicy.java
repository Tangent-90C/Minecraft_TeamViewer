package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Common local/native map diff, upload, remote projection and entity tracking policy. */
final class SharedWaypointMapSyncPolicy {
    private static final long LOCAL_SCAN_INTERVAL_MS = 1_500L;
    private static final long REMOTE_SYNC_INTERVAL_MS = 500L;
    private final Config config;
    private final GameClientBridge game;
    private final WaypointSyncGateway gateway;
    private final LongSupplier clock;
    private final Map<String, AdapterState> states = new HashMap<>();
    private final Map<String, Position3D> trackedEntityPositions = new HashMap<>();

    SharedWaypointMapSyncPolicy(Config config, GameClientBridge game, WaypointSyncGateway gateway) {
        this(config, game, gateway, System::currentTimeMillis);
    }

    SharedWaypointMapSyncPolicy(Config config, GameClientBridge game, WaypointSyncGateway gateway, LongSupplier clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.game = Objects.requireNonNull(game, "game");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void tick(List<SharedWaypointMapAdapter> adapters, Map<String, SharedWaypointInfo> remote,
              boolean enabled, ClientWorldSnapshot world) {
        long now = clock.getAsLong();
        for (SharedWaypointMapAdapter adapter : adapters) {
            AdapterState state = states.computeIfAbsent(adapter.id(), ignored -> new AdapterState());
            if (!enabled) {
                adapter.clearRemoteWaypoints();
                state.reset();
                continue;
            }
            if (!adapter.isAvailable() || !world.available()) continue;
            if (adapter.needsReconcile() && now - state.lastRemoteSync >= REMOTE_SYNC_INTERVAL_MS) {
                adapter.clearRemoteWaypoints();
                state.renderedRemoteIds.clear();
            }
            if (gateway.isConnected() && now - state.lastLocalScan >= LOCAL_SCAN_INTERVAL_MS) {
                state.lastLocalScan = now;
                syncLocal(adapter, state, world);
            }
            if (!config.isShowSharedWaypoints()) {
                adapter.clearRemoteWaypoints();
                state.renderedRemoteIds.clear();
            } else if (now - state.lastRemoteSync >= REMOTE_SYNC_INTERVAL_MS) {
                state.lastRemoteSync = now;
                syncRemote(adapter, state, world, remote);
            }
        }
    }

    void deleteRemoteWaypoints(List<SharedWaypointMapAdapter> adapters, List<String> ids) {
        for (SharedWaypointMapAdapter adapter : adapters) {
            adapter.deleteWaypoints(ids);
            AdapterState state = states.get(adapter.id());
            if (state != null) {
                for (String id : ids) state.renderedRemoteIds.remove(id);
            }
        }
    }

    void clear(List<SharedWaypointMapAdapter> adapters) {
        for (SharedWaypointMapAdapter adapter : adapters) adapter.clearRemoteWaypoints();
        states.clear();
        trackedEntityPositions.clear();
    }

    private void syncLocal(SharedWaypointMapAdapter adapter, AdapterState state, ClientWorldSnapshot world) {
        List<NativeMapWaypointSnapshot> nativeValues;
        try {
            nativeValues = adapter.listLocalWaypoints();
        } catch (RuntimeException ignored) {
            return;
        }
        Map<String, SharedWaypointInfo> current = new LinkedHashMap<>();
        for (NativeMapWaypointSnapshot value : nativeValues == null ? List.<NativeMapWaypointSnapshot>of() : nativeValues) {
            // Third-party and Lua adapters are extension boundaries and may return malformed list elements.
            //noinspection ConstantValue
            if (value == null || value.name() == null || value.name().isBlank()) continue;
            String dimension = value.dimension() == null || value.dimension().isBlank() ? world.dimension() : value.dimension();
            String seed = adapter.id() + "|" + world.localPlayerId() + "|" + dimension + "|" + value.name()
                    + "|" + value.x() + "|" + value.y() + "|" + value.z();
            String id = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
            SharedWaypointInfo previous = state.knownLocal.get(id);
            current.put(id, new SharedWaypointInfo(id, world.localPlayerId(), world.localPlayerName(), value.name(),
                    safeSymbol(value.symbol()), value.x(), value.y(), value.z(), dimension, value.color(),
                    previous == null ? clock.getAsLong() : previous.createdAt(),
                    null, null, null, null, "manual", null, adapter.id()));
        }
        if (state.baselineInitialized && config.isUploadSharedWaypoints()) {
            int ttl = config.isEnableLongTermWaypoint()
                    ? config.getLongTermWaypointTimeoutSeconds() : config.getWaypointTimeoutSeconds();
            Map<String, WaypointSyncPayload> upserts = new LinkedHashMap<>();
            current.forEach((id, value) -> {
                if (!state.knownLocal.containsKey(id)) upserts.put(id, WaypointSyncPayload.manual(value, ttl));
            });
            List<String> deletes = state.knownLocal.keySet().stream().filter(id -> !current.containsKey(id)).toList();
            if (!upserts.isEmpty()) gateway.sendWaypointUpserts(world.localPlayerId(), upserts);
            if (!deletes.isEmpty()) gateway.sendWaypointDeletes(world.localPlayerId(), deletes);
        }
        state.baselineInitialized = true;
        state.knownLocal.clear();
        state.knownLocal.putAll(current);
    }

    private void syncRemote(SharedWaypointMapAdapter adapter, AdapterState state, ClientWorldSnapshot world,
                            Map<String, SharedWaypointInfo> remote) {
        Set<String> active = new HashSet<>();
        for (Map.Entry<String, SharedWaypointInfo> entry : remote.entrySet()) {
            SharedWaypointInfo waypoint = entry.getValue();
            if (waypoint == null || waypoint.waypointId() == null || waypoint.waypointId().isBlank()
                    || (!config.isShowOwnSharedWaypointsOnMinimap() && world.localPlayerId().equals(waypoint.ownerId()))
                    || !sameDimension(world.dimension(), waypoint.dimension()) || targetsLocalPlayer(world, waypoint)) continue;
            active.add(entry.getKey());
            Position3D position = resolvePosition(adapter.id(), world, waypoint);
            adapter.upsertRemoteWaypoint(new MapWaypointCommand(entry.getKey(), decorateName(waypoint),
                    safeSymbol(waypoint.symbol()), (int) Math.floor(position.x()), (int) Math.floor(position.y()),
                    (int) Math.floor(position.z()), world.dimension(), waypoint.color(), waypoint.waypointKind(),
                    waypoint.tacticalType(), waypoint.sourceType()));
        }
        for (String id : new ArrayList<>(state.renderedRemoteIds)) {
            if (!active.contains(id)) adapter.deleteRemoteWaypoint(id);
        }
        state.renderedRemoteIds.clear();
        state.renderedRemoteIds.addAll(active);
    }

    private Position3D resolvePosition(String adapterId, ClientWorldSnapshot world, SharedWaypointInfo waypoint) {
        if (!"entity".equalsIgnoreCase(waypoint.targetType()) || waypoint.targetEntityId() == null) {
            return staticPosition(waypoint);
        }
        String key = adapterId + "|" + waypoint.waypointId();
        Position3D resolved = game.resolveEntityPosition(
                waypoint.targetEntityId(), waypoint.targetEntityName(), world.dimension()).orElse(null);
        if (resolved == null) resolved = gateway.getRemoteEntityPosition(waypoint.targetEntityId(), world.dimension());
        if (resolved == null && "minecraft:player".equalsIgnoreCase(waypoint.targetEntityType())) {
            resolved = gateway.getRemotePlayerPosition(
                    waypoint.targetEntityId(), waypoint.targetEntityName(), world.dimension());
        }
        if (resolved != null) {
            trackedEntityPositions.put(key, resolved);
            return resolved;
        }
        return trackedEntityPositions.computeIfAbsent(key, ignored -> staticPosition(waypoint));
    }

    private static Position3D staticPosition(SharedWaypointInfo waypoint) {
        return new Position3D(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5);
    }

    private static boolean targetsLocalPlayer(ClientWorldSnapshot world, SharedWaypointInfo waypoint) {
        return "entity".equalsIgnoreCase(waypoint.targetType())
                && (world.localPlayerId().toString().equals(waypoint.targetEntityId())
                || (world.localPlayerName() != null && waypoint.targetEntityName() != null
                && world.localPlayerName().equalsIgnoreCase(waypoint.targetEntityName())));
    }

    private static boolean sameDimension(String current, String waypoint) {
        return waypoint == null || waypoint.isBlank() || Objects.equals(current, waypoint);
    }

    private static String decorateName(SharedWaypointInfo waypoint) {
        String owner = waypoint.ownerName() == null || waypoint.ownerName().isBlank() ? "Unknown" : waypoint.ownerName();
        String name = waypoint.name() == null || waypoint.name().isBlank() ? "Waypoint" : waypoint.name();
        return "[TV] " + owner + ": " + name;
    }

    private static String safeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return "W";
        String value = symbol.trim();
        return value.length() > 2 ? value.substring(0, 2) : value;
    }

    private static final class AdapterState {
        private final Map<String, SharedWaypointInfo> knownLocal = new HashMap<>();
        private final Set<String> renderedRemoteIds = new HashSet<>();
        private long lastLocalScan;
        private long lastRemoteSync;
        private boolean baselineInitialized;

        private void reset() {
            knownLocal.clear();
            renderedRemoteIds.clear();
            lastLocalScan = 0L;
            lastRemoteSync = 0L;
            baselineInitialized = false;
        }
    }
}
