package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RemotePlayerProjectionCoordinator {
	private final List<RemotePlayerProjection> projections;
	private final Config config;
	private final GameClientBridge game;

	public RemotePlayerProjectionCoordinator(List<RemotePlayerProjection> projections, Config config, GameClientBridge game) {
		this.projections = List.copyOf(projections);
		this.config = config;
		this.game = game;
	}

	public void tick(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		Map<UUID, RemotePlayerInfo> filtered = filter(players);
		for (RemotePlayerProjection projection : projections) {
			if (!projection.isAvailable()) {
				continue;
			}
			projection.sync(filtered, enabled && isEnabled(projection.kind()));
		}
	}

	private Map<UUID, RemotePlayerInfo> filter(Map<UUID, RemotePlayerInfo> players) {
		ClientWorldSnapshot world = game.captureWorldSnapshot();
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
		for (RemotePlayerProjection projection : projections) {
			projection.clear();
		}
	}

	private boolean isEnabled(RemotePlayerProjection.Kind kind) {
		return switch (kind) {
			case JOURNEYMAP_BEACON -> config.isShowJourneyMapRemotePlayerBeacons();
			case JOURNEYMAP_MAP_MARKER -> config.isShowJourneyMapRemotePlayerMapMarkers();
			case XAERO_WORLD_MAP_MARKER, OTHER -> true;
		};
	}
}
