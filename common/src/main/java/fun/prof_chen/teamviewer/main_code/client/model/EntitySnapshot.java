package fun.prof_chen.teamviewer.main_code.client.model;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.ReportDataSchemas;

import java.util.Map;
import java.util.Objects;

public record EntitySnapshot(
        String id,
        Position3D position,
        Position3D velocity,
        String dimension,
        String type,
        String name,
        float width,
        float height) {
    public EntitySnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
    }

    public Map<String, Object> toProtocolMap() {
        return new ReportDataSchemas.EntityDataPayload(
                position.x(), position.y(), position.z(),
                velocity.x(), velocity.y(), velocity.z(),
                dimension, type, name, width, height
        ).toMap();
    }
}
