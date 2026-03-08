package fun.prof_chen.teamviewer.multipleplayeresp.sync.api;

import fun.prof_chen.teamviewer.multipleplayeresp.model.Position3D;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WaypointSyncGateway {
	boolean isConnected();

	void addWaypointUpdateListener(WaypointUpdateListener listener);

	void removeWaypointUpdateListener(WaypointUpdateListener listener);

	void sendWaypointUpserts(UUID submitPlayerId, Map<String, WaypointSyncPayload> payloads);

	void sendWaypointDeletes(UUID submitPlayerId, List<String> waypointIds);

	void sendWaypointEntityDeathCancel(UUID submitPlayerId, List<String> targetEntityIds);

	Position3D getRemoteEntityPosition(String entityId, String expectedDimension);

	Position3D getRemotePlayerPosition(String playerId, String playerName, String expectedDimension);
}