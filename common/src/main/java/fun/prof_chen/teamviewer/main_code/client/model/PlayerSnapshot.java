package fun.prof_chen.teamviewer.main_code.client.model;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.ReportDataSchemas;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PlayerSnapshot(
        UUID id,
        Position3D position,
        Position3D velocity,
        String dimension,
        String name,
        float health,
        float maxHealth,
        float armor,
        boolean riding,
        float width,
        float height) {
    public PlayerSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
    }

    public Map<String, Object> toProtocolMap() {
        return new ReportDataSchemas.PlayerDataPayload(
                position.x(), position.y(), position.z(),
                velocity.x(), velocity.y(), velocity.z(),
                dimension, name, id.toString(), health, maxHealth, armor, riding, width, height
        ).toMap();
    }
}
