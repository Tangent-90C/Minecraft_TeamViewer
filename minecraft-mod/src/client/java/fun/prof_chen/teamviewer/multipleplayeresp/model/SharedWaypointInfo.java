package fun.prof_chen.teamviewer.multipleplayeresp.model;

import java.util.UUID;

public record SharedWaypointInfo(
		String waypointId,
		UUID ownerId,
		String ownerName,
		String name,
		String symbol,
		int x,
		int y,
		int z,
		String dimension,
		int color,
		long createdAt,
		String targetType,
		String targetEntityId,
		String targetEntityType,
		String targetEntityName,
		String waypointKind) {
}