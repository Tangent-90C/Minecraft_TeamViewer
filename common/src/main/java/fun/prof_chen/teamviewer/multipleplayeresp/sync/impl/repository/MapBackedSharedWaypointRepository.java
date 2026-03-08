package fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.repository;

import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.SharedWaypointRepository;

import java.util.Map;

public final class MapBackedSharedWaypointRepository extends MapBackedCrudRepository<String, SharedWaypointInfo>
		implements SharedWaypointRepository {
	public MapBackedSharedWaypointRepository(Map<String, SharedWaypointInfo> backingMap) {
		super(backingMap);
	}
}