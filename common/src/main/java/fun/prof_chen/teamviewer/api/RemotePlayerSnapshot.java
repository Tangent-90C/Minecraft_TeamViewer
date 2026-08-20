package fun.prof_chen.teamviewer.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable, loader-neutral view of one remote player. */
public record RemotePlayerSnapshot(
        UUID uuid,
        String name,
        String dimension,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        float health,
        float maxHealth,
        float armor,
        boolean riding,
        float width,
        float height,
        PlayerRelation relation,
        RemotePlayerPositionSource positionSource) {

    public RemotePlayerSnapshot {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(positionSource, "positionSource");
    }

    /** Binary-compatible constructor retained for API v1/v2 integrations. */
    public RemotePlayerSnapshot(
            UUID uuid, String name, String dimension,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            float health, float maxHealth, float armor,
            boolean riding, float width, float height, PlayerRelation relation) {
        this(uuid, name, dimension, x, y, z, velocityX, velocityY, velocityZ,
                health, maxHealth, armor, riding, width, height, relation,
                RemotePlayerPositionSource.unknown());
    }
}
