package fun.prof_chen.teamviewer.main_code.model;

import java.util.Objects;
import java.util.UUID;

/** Immutable last-known position reported by an authoritative external source. */
public record LastSeenPlayerInfo(
        UUID uuid,
        Position3D position,
        String dimension,
        String name,
        long lastSeenAtUtcMs,
        long positionObservedAtUtcMs,
        long offlineDetectedAtUtcMs) {
    public LastSeenPlayerInfo {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(position, "position");
        dimension = Objects.requireNonNull(dimension, "dimension");
        name = Objects.requireNonNull(name, "name");
    }
}
