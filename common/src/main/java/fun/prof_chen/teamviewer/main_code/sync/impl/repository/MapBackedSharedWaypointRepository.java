package fun.prof_chen.teamviewer.main_code.sync.impl.repository;

import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.sync.api.SharedWaypointRepository;

import java.util.Map;

public final class MapBackedSharedWaypointRepository extends MapBackedCrudRepository<String, SharedWaypointInfo>
		implements SharedWaypointRepository {
	public MapBackedSharedWaypointRepository(Map<String, SharedWaypointInfo> backingMap) {
		super(backingMap);
	}
}