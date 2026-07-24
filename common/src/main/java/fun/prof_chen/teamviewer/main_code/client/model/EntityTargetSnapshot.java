package fun.prof_chen.teamviewer.main_code.client.model;

import fun.prof_chen.teamviewer.main_code.model.Position3D;

public record EntityTargetSnapshot(
        Position3D position,
        String entityId,
        String entityType,
        String entityName,
        boolean living,
        boolean dead) {
}
