package fun.prof_chen.teamviewer.multipleplayeresp.sync.api;

import fun.prof_chen.teamviewer.multipleplayeresp.model.ReportDataSchemas;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record WaypointSyncPayload(SharedWaypointInfo waypoint, Map<String, Object> protocolData) {
	public WaypointSyncPayload {
		waypoint = Objects.requireNonNull(waypoint, "waypoint");
		if (protocolData == null || protocolData.isEmpty()) {
			protocolData = Map.of();
		} else {
			Map<String, Object> sanitized = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : protocolData.entrySet()) {
				if (entry.getKey() == null || entry.getValue() == null) {
					continue;
				}
				sanitized.put(entry.getKey(), entry.getValue());
			}
			protocolData = sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
		}
	}

	public static WaypointSyncPayload manual(SharedWaypointInfo waypoint, int ttlSeconds) {
		ReportDataSchemas.WaypointDataPayload payload = new ReportDataSchemas.WaypointDataPayload(
				waypoint.x(),
				waypoint.y(),
				waypoint.z(),
				waypoint.dimension(),
				waypoint.name(),
				waypoint.symbol(),
				waypoint.color(),
				waypoint.ownerId() == null ? null : waypoint.ownerId().toString(),
				waypoint.ownerName(),
				waypoint.createdAt(),
				ttlSeconds,
				"manual",
				null,
				null,
				waypoint.targetType(),
				waypoint.targetEntityId(),
				waypoint.targetEntityType(),
				waypoint.targetEntityName(),
				null,
				null,
				waypoint.tacticalType(),
				waypoint.sourceType());
		return new WaypointSyncPayload(waypoint, payload.toMap());
	}

	public static WaypointSyncPayload quickMark(SharedWaypointInfo waypoint, int ttlSeconds, int maxQuickMarks) {
		ReportDataSchemas.WaypointDataPayload payload = new ReportDataSchemas.WaypointDataPayload(
				waypoint.x(),
				waypoint.y(),
				waypoint.z(),
				waypoint.dimension(),
				waypoint.name(),
				waypoint.symbol(),
				waypoint.color(),
				waypoint.ownerId() == null ? null : waypoint.ownerId().toString(),
				waypoint.ownerName(),
				waypoint.createdAt(),
				ttlSeconds,
				waypoint.waypointKind(),
				null,
				maxQuickMarks,
				waypoint.targetType(),
				waypoint.targetEntityId(),
				waypoint.targetEntityType(),
				waypoint.targetEntityName(),
				null,
				null,
				waypoint.tacticalType(),
				waypoint.sourceType());
		return new WaypointSyncPayload(waypoint, payload.toMap());
	}
}