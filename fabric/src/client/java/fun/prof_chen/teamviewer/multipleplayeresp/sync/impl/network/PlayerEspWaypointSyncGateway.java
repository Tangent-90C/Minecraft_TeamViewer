package fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.network;

import fun.prof_chen.teamviewer.multipleplayeresp.model.Position3D;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.network.PlayerESPNetworkManager;
import fun.prof_chen.teamviewer.multipleplayeresp.platform.minecraft.MinecraftPositionAdapter;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.WaypointSyncGateway;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.WaypointSyncPayload;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.WaypointUpdateListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerEspWaypointSyncGateway implements WaypointSyncGateway {
	private final PlayerESPNetworkManager delegate;
	private final Map<WaypointUpdateListener, PlayerESPNetworkManager.WaypointUpdateListener> listenerMap = new ConcurrentHashMap<>();

	public PlayerEspWaypointSyncGateway(PlayerESPNetworkManager delegate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public boolean isConnected() {
		return delegate.isConnected();
	}

	@Override
	public void addWaypointUpdateListener(WaypointUpdateListener listener) {
		if (listener == null) {
			return;
		}
		PlayerESPNetworkManager.WaypointUpdateListener adapted = new PlayerESPNetworkManager.WaypointUpdateListener() {
			@Override
			public void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints) {
				listener.onWaypointsReceived(waypoints);
			}

			@Override
			public void onWaypointsDeleted(List<String> waypointIds) {
				listener.onWaypointsDeleted(waypointIds);
			}
		};
		PlayerESPNetworkManager.WaypointUpdateListener previous = listenerMap.putIfAbsent(listener, adapted);
		if (previous == null) {
			delegate.addWaypointUpdateListener(adapted);
		}
	}

	@Override
	public void removeWaypointUpdateListener(WaypointUpdateListener listener) {
		if (listener == null) {
			return;
		}
		PlayerESPNetworkManager.WaypointUpdateListener adapted = listenerMap.remove(listener);
		if (adapted != null) {
			delegate.removeWaypointUpdateListener(adapted);
		}
	}

	@Override
	public void sendWaypointUpserts(UUID submitPlayerId, Map<String, WaypointSyncPayload> payloads) {
		if (payloads == null || payloads.isEmpty()) {
			return;
		}
		Map<String, Map<String, Object>> encoded = new HashMap<>();
		for (Map.Entry<String, WaypointSyncPayload> entry : payloads.entrySet()) {
			WaypointSyncPayload payload = entry.getValue();
			if (entry.getKey() == null || payload == null) {
				continue;
			}
			encoded.put(entry.getKey(), payload.protocolData());
		}
		delegate.sendWaypointsUpdate(submitPlayerId, encoded);
	}

	@Override
	public void sendWaypointDeletes(UUID submitPlayerId, List<String> waypointIds) {
		delegate.sendWaypointsDelete(submitPlayerId, waypointIds);
	}

	@Override
	public void sendWaypointEntityDeathCancel(UUID submitPlayerId, List<String> targetEntityIds) {
		delegate.sendWaypointEntityDeathCancel(submitPlayerId, targetEntityIds);
	}

	@Override
	public Position3D getRemoteEntityPosition(String entityId, String expectedDimension) {
		return MinecraftPositionAdapter.fromVec3d(delegate.getRemoteEntityPosition(entityId, expectedDimension));
	}

	@Override
	public Position3D getRemotePlayerPosition(String playerId, String playerName, String expectedDimension) {
		return MinecraftPositionAdapter.fromVec3d(delegate.getRemotePlayerPosition(playerId, playerName, expectedDimension));
	}
}