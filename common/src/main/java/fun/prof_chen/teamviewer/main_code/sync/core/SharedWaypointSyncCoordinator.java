package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.mapbridge.abstraction.SharedWaypointMapBridgeConfig;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.SharedWaypointRepository;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointUpdateListener;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SharedWaypointSyncCoordinator {
	private final SharedWaypointRepository repository;
	private final WaypointSyncGateway gateway;
	private final List<SharedWaypointMapAdapter> adapters;
	private final WaypointUpdateListener inboundListener = new WaypointUpdateListener() {
		@Override
		public void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints) {
			repository.putAll(waypoints);
		}

		@Override
		public void onWaypointsDeleted(List<String> waypointIds) {
			repository.removeAll(waypointIds);
			deleteManagedWaypoints(waypointIds);
		}
	};

	public SharedWaypointSyncCoordinator(
			SharedWaypointRepository repository,
			WaypointSyncGateway gateway,
			List<SharedWaypointMapAdapter> adapters) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.adapters = List.copyOf(adapters);
	}

	public void start() {
		gateway.addWaypointUpdateListener(inboundListener);
	}

	public void stop() {
		gateway.removeWaypointUpdateListener(inboundListener);
	}

	public void tick(boolean enabled, SharedWaypointMapBridgeConfig config) {
		Map<String, SharedWaypointInfo> snapshot = repository.snapshot();
		for (SharedWaypointMapAdapter adapter : adapters) {
			if (!adapter.isAvailable()) {
				continue;
			}
			adapter.tick(gateway, snapshot, enabled, config);
		}
	}

	public void upsertLocalWaypoints(UUID submitPlayerId, Map<String, WaypointSyncPayload> payloads) {
		if (payloads == null || payloads.isEmpty()) {
			return;
		}
		for (Map.Entry<String, WaypointSyncPayload> entry : payloads.entrySet()) {
			WaypointSyncPayload payload = entry.getValue();
			if (payload == null) {
				continue;
			}
			repository.put(entry.getKey(), payload.waypoint());
		}
		gateway.sendWaypointUpserts(submitPlayerId, payloads);
	}

	public void deleteLocalWaypoints(UUID submitPlayerId, List<String> waypointIds) {
		repository.removeAll(waypointIds);
		deleteManagedWaypoints(waypointIds);
		gateway.sendWaypointDeletes(submitPlayerId, waypointIds);
	}

	public void cancelEntityDeath(
			UUID submitPlayerId,
			List<String> waypointIds,
			List<String> targetEntityIds) {
		if (waypointIds != null && !waypointIds.isEmpty()) {
			repository.removeAll(waypointIds);
			deleteManagedWaypoints(waypointIds);
		}
		if (targetEntityIds != null && !targetEntityIds.isEmpty()) {
			gateway.sendWaypointEntityDeathCancel(submitPlayerId, targetEntityIds);
		}
	}

	public void deleteManagedWaypoint(String waypointId) {
		deleteManagedWaypoints(List.of(waypointId));
	}

	public void deleteManagedWaypoints(List<String> waypointIds) {
		if (waypointIds == null || waypointIds.isEmpty()) {
			return;
		}
		for (SharedWaypointMapAdapter adapter : adapters) {
			adapter.deleteWaypoints(waypointIds);
		}
	}

	public void clear() {
		repository.clear();
		for (SharedWaypointMapAdapter adapter : adapters) {
			adapter.clear();
		}
	}
}
