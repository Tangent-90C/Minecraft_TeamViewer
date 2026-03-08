package fun.prof_chen.teamviewer.multipleplayeresp.sync.core;

import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.RemotePlayerProjection;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RemotePlayerProjectionCoordinator {
	private final List<RemotePlayerProjection> projections;

	public RemotePlayerProjectionCoordinator(List<RemotePlayerProjection> projections) {
		this.projections = List.copyOf(projections);
	}

	public void tick(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		for (RemotePlayerProjection projection : projections) {
			if (!projection.isAvailable()) {
				continue;
			}
			projection.sync(players, enabled);
		}
	}

	public void clear() {
		for (RemotePlayerProjection projection : projections) {
			projection.clear();
		}
	}
}