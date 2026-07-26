package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.SharedWaypointRepository;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncGateway;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.main_code.sync.api.WaypointUpdateListener;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SharedWaypointSyncCoordinator {
	private final SharedWaypointRepository repository;
	private final WaypointSyncGateway gateway;
	private final IntegrationRegistry integrations;
	private final SharedWaypointMapSyncPolicy mapPolicy;
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
			IntegrationRegistry integrations,
			Config config,
			GameClientBridge game) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.integrations = Objects.requireNonNull(integrations, "integrations");
		this.mapPolicy = new SharedWaypointMapSyncPolicy(config, game, gateway);
	}

	public void start() {
		gateway.addWaypointUpdateListener(inboundListener);
	}

	public void stop() {
		gateway.removeWaypointUpdateListener(inboundListener);
	}

	public void tick(boolean enabled, ClientWorldSnapshot world) {
		List<SharedWaypointMapAdapter> adapters = integrations.activeSharedWaypointAdapters();
		if (adapters.isEmpty()) return;
		Map<String, SharedWaypointInfo> snapshot = repository.snapshot();
		mapPolicy.tick(adapters, snapshot, enabled, world);
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
		mapPolicy.deleteRemoteWaypoints(integrations.activeSharedWaypointAdapters(), waypointIds);
	}

	public void clear() {
		repository.clear();
		mapPolicy.clear(integrations.activeSharedWaypointAdapters());
	}
}
