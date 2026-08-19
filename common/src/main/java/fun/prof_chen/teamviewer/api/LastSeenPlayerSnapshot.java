package fun.prof_chen.teamviewer.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable, loader-neutral view of an offline player's last known position. */
public record LastSeenPlayerSnapshot(
        UUID uuid,
        String name,
        String dimension,
        double x,
        double y,
        double z,
        long lastSeenAtUtcMs,
        long positionObservedAtUtcMs,
        long offlineDetectedAtUtcMs) {
    public LastSeenPlayerSnapshot {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
    }
}
