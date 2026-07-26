package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.sync.api.RemotePlayerRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RemotePlayerProjectionCoordinator {
	private final IntegrationRegistry integrations;

	public RemotePlayerProjectionCoordinator(IntegrationRegistry integrations) {
		this.integrations = integrations;
	}

	public void tick(RemotePlayerRepository repository, boolean enabled, ClientWorldSnapshot world) {
		List<RemotePlayerProjection> projections = integrations.activeRemotePlayerProjections();
		if (projections.isEmpty()) return;
		Map<UUID, RemotePlayerInfo> players = repository.snapshot();
		Map<UUID, RemotePlayerInfo> filtered = filter(players, world);
		for (RemotePlayerProjection projection : projections) {
			if (!projection.isAvailable()) {
				continue;
			}
			projection.sync(filtered, enabled);
		}
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
		for (RemotePlayerProjection projection : integrations.activeRemotePlayerProjections()) {
			projection.clear();
		}
	}

}
