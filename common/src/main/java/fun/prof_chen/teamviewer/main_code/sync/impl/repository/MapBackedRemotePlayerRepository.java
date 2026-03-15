package fun.prof_chen.teamviewer.main_code.sync.impl.repository;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.RemotePlayerRepository;

import java.util.Map;
import java.util.UUID;

public final class MapBackedRemotePlayerRepository extends MapBackedCrudRepository<UUID, RemotePlayerInfo>
		implements RemotePlayerRepository {
	public MapBackedRemotePlayerRepository(Map<UUID, RemotePlayerInfo> backingMap) {
		super(backingMap);
	}
}