package fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.repository;

import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.RemotePlayerRepository;

import java.util.Map;
import java.util.UUID;

public final class MapBackedRemotePlayerRepository extends MapBackedCrudRepository<UUID, RemotePlayerInfo>
		implements RemotePlayerRepository {
	public MapBackedRemotePlayerRepository(Map<UUID, RemotePlayerInfo> backingMap) {
		super(backingMap);
	}
}