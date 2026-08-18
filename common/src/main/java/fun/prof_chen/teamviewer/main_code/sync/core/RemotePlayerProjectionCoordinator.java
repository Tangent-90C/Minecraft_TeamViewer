package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerRelationView;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.sync.api.RemotePlayerRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class RemotePlayerProjectionCoordinator {
	private static final long AVAILABILITY_PROBE_INTERVAL_NANOS = 1_000_000_000L;
	private static final long RECONCILE_RETRY_INTERVAL_NANOS = 1_000_000_000L;
	private final IntegrationRegistry integrations;
	private final Function<UUID, PlayerRelationView> relationResolver;
	private final LongSupplier nanoClock;
	private List<RemotePlayerProjection> cachedProjections = List.of();
	private long lastAvailabilityProbeNanos = Long.MIN_VALUE;
	private long lastReconcileRetryNanos = Long.MIN_VALUE;
	private ProjectionState lastState;

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

	public void tick(RemotePlayerRepository repository, boolean enabled, ClientWorldSnapshot world) {
		List<RemotePlayerProjection> projections = projections();
		if (projections.isEmpty()) return;
		Map<UUID, RemotePlayerInfo> players = repository.snapshot();
		Map<UUID, RemotePlayerInfo> filtered = filter(players, world);
		Map<UUID, PlayerRelationView> relations = new LinkedHashMap<>();
		for (UUID playerId : filtered.keySet()) {
			PlayerRelationView relation = relationResolver.apply(playerId);
			if (relation != null) relations.put(playerId, relation);
		}
		relations = Map.copyOf(relations);
		ProjectionState state = new ProjectionState(filtered, relations, enabled,
				world != null && world.available(), world == null ? null : world.dimension(),
				world == null ? null : world.localPlayerId());
		boolean stateChanged = !state.equals(lastState);
		long now = nanoClock.getAsLong();
		boolean retryDue = lastReconcileRetryNanos == Long.MIN_VALUE
				|| now - lastReconcileRetryNanos >= RECONCILE_RETRY_INTERVAL_NANOS;
		if (!stateChanged && !retryDue) return;
		if (stateChanged) lastState = state;
		boolean retried = false;
		for (RemotePlayerProjection projection : projections) {
			if (!projection.isAvailable()) {
				continue;
			}
			if (!stateChanged && !projection.needsReconcile()) continue;
			projection.syncResolved(filtered, relations, enabled);
			if (!stateChanged) retried = true;
		}
		if (stateChanged || retried) lastReconcileRetryNanos = now;
	}

	private Map<UUID, RemotePlayerInfo> filter(
			Map<UUID, RemotePlayerInfo> players, ClientWorldSnapshot world) {
		if (!world.available() || players == null || players.isEmpty()) return Map.of();
		Map<UUID, RemotePlayerInfo> result = new LinkedHashMap<>();
		for (Map.Entry<UUID, RemotePlayerInfo> entry : players.entrySet()) {
			RemotePlayerInfo value = entry.getValue();
			if (value == null || value.uuid() == null || value.position() == null
					|| value.uuid().equals(world.localPlayerId())
					|| (value.dimension() != null && !value.dimension().isBlank()
					&& !value.dimension().equals(world.dimension()))) continue;
			result.put(entry.getKey(), value);
		}
		return result;
	}

	public void clear() {
		for (RemotePlayerProjection projection : projections()) {
			projection.clear();
		}
		invalidate();
	}

	public void invalidate() {
		cachedProjections = List.of();
		lastAvailabilityProbeNanos = Long.MIN_VALUE;
		lastReconcileRetryNanos = Long.MIN_VALUE;
		lastState = null;
	}

	private List<RemotePlayerProjection> projections() {
		long now = nanoClock.getAsLong();
		if (lastAvailabilityProbeNanos == Long.MIN_VALUE
				|| now - lastAvailabilityProbeNanos >= AVAILABILITY_PROBE_INTERVAL_NANOS) {
			List<RemotePlayerProjection> previous = cachedProjections;
			List<RemotePlayerProjection> current = integrations.activeRemotePlayerProjections();
			for (RemotePlayerProjection projection : previous) {
				if (!current.contains(projection)) projection.clear();
			}
			cachedProjections = current;
			lastAvailabilityProbeNanos = now;
			if (!previous.equals(cachedProjections)) lastState = null;
		}
		return cachedProjections;
	}

	private record ProjectionState(
			Map<UUID, RemotePlayerInfo> players,
			Map<UUID, PlayerRelationView> relations,
			boolean enabled,
			boolean worldAvailable,
			String dimension,
			UUID localPlayerId) {
		private ProjectionState {
			players = Map.copyOf(players);
			relations = Map.copyOf(relations);
		}
	}

}
