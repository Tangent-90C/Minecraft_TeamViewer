package fun.prof_chen.teamviewer.main_code.client.entity;

import java.util.UUID;

/**
 * Allocation-free target used by Minecraft adapters while walking the loaded entity collection.
 * A target instance is owned by one thread until it is submitted to the entity report pipeline.
 */
public interface EntityCaptureTarget {
    void begin(UUID submitPlayerId, String dimension, int scannedEntityCount);

    void accept(
            UUID id,
            double x, double y, double z,
            double vx, double vy, double vz,
            String entityType,
            String customName,
            float width,
            float height);

    void finish(int scannedEntityCount);
}
