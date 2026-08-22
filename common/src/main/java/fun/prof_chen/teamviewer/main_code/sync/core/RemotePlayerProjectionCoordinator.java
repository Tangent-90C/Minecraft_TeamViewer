package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerRelationView;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.model.LastSeenPlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.LastSeenPlayerRepository;
import fun.prof_chen.teamviewer.main_code.sync.api.RemotePlayerRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Projects relay state into optional native maps. Official clients supply immutable snapshots with
 * generations, so unchanged offline collections are never copied, filtered or converted per tick.
 */
public final class RemotePlayerProjectionCoordinator {
    private static final long AVAILABILITY_PROBE_INTERVAL_NANOS = 1_000_000_000L;
    private static final long RECONCILE_RETRY_INTERVAL_NANOS = 1_000_000_000L;
    private final IntegrationRegistry integrations;
    private final Function<UUID, PlayerRelationView> relationResolver;
    private final LongSupplier nanoClock;
    private List<RemotePlayerProjection> cachedProjections = List.of();
    private long lastAvailabilityProbeNanos = Long.MIN_VALUE;
    private long lastReconcileRetryNanos = Long.MIN_VALUE;
    private OnlineState lastOnlineState;
    private OfflineState lastOfflineState;
    private Map<UUID, RemotePlayerInfo> lastFilteredPlayers = Map.of();
    private Map<UUID, PlayerRelationView> lastPlayerRelations = Map.of();
    private Map<UUID, LastSeenPlayerInfo> lastFilteredLastSeenPlayers = Map.of();
    private Map<UUID, PlayerRelationView> lastLastSeenRelations = Map.of();

    public RemotePlayerProjectionCoordinator(IntegrationRegistry integrations) {
        this(integrations, ignored -> null, System::nanoTime);
    }

    public RemotePlayerProjectionCoordinator(
            IntegrationRegistry integrations, Function<UUID, PlayerRelationView> relationResolver) {
        this(integrations, relationResolver, System::nanoTime);
    }

    RemotePlayerProjectionCoordinator(
            IntegrationRegistry integrations,
            Function<UUID, PlayerRelationView> relationResolver,
            LongSupplier nanoClock) {
        this.integrations = integrations;
        this.relationResolver = relationResolver == null ? ignored -> null : relationResolver;
        this.nanoClock = nanoClock == null ? System::nanoTime : nanoClock;
    }

    /** Compatibility entry point for external callers without versioned state snapshots. */
    public void tick(RemotePlayerRepository repository, boolean enabled, ClientWorldSnapshot world) {
        tick(repository, null, enabled, false, world);
    }

    /** Compatibility entry point for external callers without versioned state snapshots. */
    public void tick(
            RemotePlayerRepository repository,
            LastSeenPlayerRepository lastSeenRepository,
            boolean enabled,
            boolean lastSeenEnabled,
            ClientWorldSnapshot world) {
        Map<UUID, RemotePlayerInfo> players = repository == null ? Map.of() : repository.snapshot();
        Map<UUID, LastSeenPlayerInfo> lastSeen =
                lastSeenRepository == null ? Map.of() : lastSeenRepository.snapshot();
        // Official clients use the versioned overload below. Hashing is only compatibility fallback.
        tick(players, players.hashCode(), lastSeen, lastSeen.hashCode(), enabled, lastSeenEnabled, world, true);
    }

    /** Versioned common-runtime entry point. */
    public void tick(
            Map<UUID, RemotePlayerInfo> players,
            long playerGeneration,
            Map<UUID, LastSeenPlayerInfo> lastSeenPlayers,
            long lastSeenGeneration,
            boolean enabled,
            boolean lastSeenEnabled,
            ClientWorldSnapshot world) {
        tick(players, playerGeneration, lastSeenPlayers, lastSeenGeneration,
                enabled, lastSeenEnabled, world, false);
    }

    private void tick(
            Map<UUID, RemotePlayerInfo> players,
            long playerGeneration,
            Map<UUID, LastSeenPlayerInfo> lastSeenPlayers,
            long lastSeenGeneration,
            boolean enabled,
            boolean lastSeenEnabled,
            ClientWorldSnapshot world,
            boolean detectCompatibilityRelationChanges) {
        List<RemotePlayerProjection> projections = projections();
        if (projections.isEmpty()) return;

        OnlineState onlineState = OnlineState.of(playerGeneration, enabled, world);
        OfflineState offlineState = OfflineState.of(lastSeenGeneration, enabled, lastSeenEnabled, world);
        boolean onlineChanged = !onlineState.equals(lastOnlineState);
        boolean offlineChanged = !offlineState.equals(lastOfflineState);
        if (onlineChanged) {
            lastFilteredPlayers = filter(players, world);
            lastPlayerRelations = resolveRelations(lastFilteredPlayers.keySet());
            lastOnlineState = onlineState;
        }
        if (offlineChanged) {
            lastFilteredLastSeenPlayers = filterLastSeen(lastSeenPlayers, players, world);
            lastLastSeenRelations = resolveRelations(lastFilteredLastSeenPlayers.keySet());
            lastOfflineState = offlineState;
        }
        // Repositories predating versioned relation invalidation may change resolver output without
        // changing their data snapshot. Preserve that public entry point's observable behavior while
        // keeping the official versioned path allocation-free on unchanged ticks.
        if (detectCompatibilityRelationChanges && !onlineChanged) {
            Map<UUID, PlayerRelationView> relations = resolveRelations(lastFilteredPlayers.keySet());
            if (!relations.equals(lastPlayerRelations)) {
                lastPlayerRelations = relations;
                onlineChanged = true;
            }
        }
        if (detectCompatibilityRelationChanges && !offlineChanged) {
            Map<UUID, PlayerRelationView> relations = resolveRelations(lastFilteredLastSeenPlayers.keySet());
            if (!relations.equals(lastLastSeenRelations)) {
                lastLastSeenRelations = relations;
                offlineChanged = true;
            }
        }

        long now = nanoClock.getAsLong();
        boolean retryDue = lastReconcileRetryNanos == Long.MIN_VALUE
                || now - lastReconcileRetryNanos >= RECONCILE_RETRY_INTERVAL_NANOS;
        if (!onlineChanged && !offlineChanged && !retryDue) return;

        boolean retried = false;
        for (RemotePlayerProjection projection : projections) {
            if (!projection.isAvailable()) continue;
            boolean retry = !onlineChanged && !offlineChanged && projection.needsReconcile();
            if (!onlineChanged && !offlineChanged && !retry) continue;
            if (onlineChanged || retry) {
                projection.syncResolved(lastFilteredPlayers, lastPlayerRelations, enabled);
            }
            if (offlineChanged || retry) {
                projection.syncLastSeenResolved(lastFilteredLastSeenPlayers, lastLastSeenRelations,
                        enabled && lastSeenEnabled);
            }
            retried |= retry;
        }
        if (onlineChanged || offlineChanged || retried) lastReconcileRetryNanos = now;
    }

    private Map<UUID, LastSeenPlayerInfo> filterLastSeen(
            Map<UUID, LastSeenPlayerInfo> players,
            Map<UUID, RemotePlayerInfo> onlinePlayers,
            ClientWorldSnapshot world) {
        if (world == null || !world.available() || players == null || players.isEmpty()) return Map.of();
        Map<UUID, LastSeenPlayerInfo> result = new LinkedHashMap<>();
        Map<UUID, RemotePlayerInfo> online = onlinePlayers == null ? Map.of() : onlinePlayers;
        for (Map.Entry<UUID, LastSeenPlayerInfo> entry : players.entrySet()) {
            LastSeenPlayerInfo value = entry.getValue();
            if (value == null || value.uuid() == null || value.position() == null
                    || value.uuid().equals(world.localPlayerId()) || entry.getKey().equals(world.localPlayerId())
                    || online.containsKey(value.uuid()) || online.containsKey(entry.getKey())
                    || (value.dimension() != null && !value.dimension().isBlank()
                    && !value.dimension().equals(world.dimension()))) continue;
            result.put(entry.getKey(), value);
        }
        return Map.copyOf(result);
    }

    private Map<UUID, PlayerRelationView> resolveRelations(Iterable<UUID> playerIds) {
        Map<UUID, PlayerRelationView> result = new LinkedHashMap<>();
        for (UUID playerId : playerIds) {
            PlayerRelationView relation = relationResolver.apply(playerId);
            if (relation != null) result.put(playerId, relation);
        }
        return Map.copyOf(result);
    }

    private Map<UUID, RemotePlayerInfo> filter(
            Map<UUID, RemotePlayerInfo> players, ClientWorldSnapshot world) {
        if (world == null || !world.available() || players == null || players.isEmpty()) return Map.of();
        Map<UUID, RemotePlayerInfo> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, RemotePlayerInfo> entry : players.entrySet()) {
            RemotePlayerInfo value = entry.getValue();
            if (value == null || value.uuid() == null || value.position() == null
                    || value.uuid().equals(world.localPlayerId())
                    || (value.dimension() != null && !value.dimension().isBlank()
                    && !value.dimension().equals(world.dimension()))) continue;
            result.put(entry.getKey(), value);
        }
        return Map.copyOf(result);
    }

    public void clear() {
        for (RemotePlayerProjection projection : projections()) {
            projection.clear();
            projection.syncLastSeenResolved(Map.of(), Map.of(), false);
        }
        invalidate();
    }

    public void invalidate() {
        cachedProjections = List.of();
        lastAvailabilityProbeNanos = Long.MIN_VALUE;
        lastReconcileRetryNanos = Long.MIN_VALUE;
        clearState();
    }

    private void clearState() {
        lastOnlineState = null;
        lastOfflineState = null;
        lastFilteredPlayers = Map.of();
        lastPlayerRelations = Map.of();
        lastFilteredLastSeenPlayers = Map.of();
        lastLastSeenRelations = Map.of();
    }

    private List<RemotePlayerProjection> projections() {
        long now = nanoClock.getAsLong();
        if (lastAvailabilityProbeNanos == Long.MIN_VALUE
                || now - lastAvailabilityProbeNanos >= AVAILABILITY_PROBE_INTERVAL_NANOS) {
            List<RemotePlayerProjection> previous = cachedProjections;
            List<RemotePlayerProjection> current = integrations.activeRemotePlayerProjections();
            for (RemotePlayerProjection projection : previous) {
                if (!current.contains(projection)) {
                    projection.clear();
                    projection.syncLastSeenResolved(Map.of(), Map.of(), false);
                }
            }
            cachedProjections = current;
            lastAvailabilityProbeNanos = now;
            if (!previous.equals(cachedProjections)) clearState();
        }
        return cachedProjections;
    }

    private record OnlineState(long generation, boolean enabled, boolean worldAvailable,
                               String dimension, UUID localPlayerId) {
        static OnlineState of(long generation, boolean enabled, ClientWorldSnapshot world) {
            return new OnlineState(generation, enabled, world != null && world.available(),
                    world == null ? null : world.dimension(), world == null ? null : world.localPlayerId());
        }
    }

    private record OfflineState(long generation, boolean enabled, boolean lastSeenEnabled,
                                boolean worldAvailable, String dimension, UUID localPlayerId) {
        static OfflineState of(long generation, boolean enabled, boolean lastSeenEnabled,
                               ClientWorldSnapshot world) {
            return new OfflineState(generation, enabled, lastSeenEnabled,
                    world != null && world.available(), world == null ? null : world.dimension(),
                    world == null ? null : world.localPlayerId());
        }
    }
}
