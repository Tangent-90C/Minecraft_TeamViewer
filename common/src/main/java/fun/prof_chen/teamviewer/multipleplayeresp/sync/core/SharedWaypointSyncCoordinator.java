package fun.prof_chen.teamviewer.multipleplayeresp.sync.core;

import fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction.SharedWaypointBridgeConfig;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.SharedWaypointMinimapAdapter;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.SharedWaypointRepository;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.WaypointSyncGateway;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.WaypointUpdateListener;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SharedWaypointSyncCoordinator {
	private final SharedWaypointRepository repository;
	private final WaypointSyncGateway gateway;
	private final List<SharedWaypointMinimapAdapter> adapters;
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
			List<SharedWaypointMinimapAdapter> adapters) {
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

	public void tick(boolean enabled, SharedWaypointBridgeConfig config) {
		Map<String, SharedWaypointInfo> snapshot = repository.snapshot();
		for (SharedWaypointMinimapAdapter adapter : adapters) {
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

	public void deleteManagedWaypoint(String waypointId) {
		deleteManagedWaypoints(List.of(waypointId));
	}

	public void deleteManagedWaypoints(List<String> waypointIds) {
		if (waypointIds == null || waypointIds.isEmpty()) {
			return;
		}
		for (SharedWaypointMinimapAdapter adapter : adapters) {
			adapter.deleteWaypoints(waypointIds);
		}
	}

	public void clear() {
		repository.clear();
		for (SharedWaypointMinimapAdapter adapter : adapters) {
			adapter.clear();
		}
	}
}