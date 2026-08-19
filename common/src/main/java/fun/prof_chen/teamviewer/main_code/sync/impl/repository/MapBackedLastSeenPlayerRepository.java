package fun.prof_chen.teamviewer.main_code.sync.impl.repository;

import fun.prof_chen.teamviewer.main_code.model.LastSeenPlayerInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.LastSeenPlayerRepository;

import java.util.Map;
import java.util.UUID;

public final class MapBackedLastSeenPlayerRepository extends MapBackedCrudRepository<UUID, LastSeenPlayerInfo>
        implements LastSeenPlayerRepository {
    public MapBackedLastSeenPlayerRepository(Map<UUID, LastSeenPlayerInfo> backingMap) {
        super(backingMap);
    }
}
