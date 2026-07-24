package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;

import java.util.Optional;

/**
 * Boundary for reads and commands that require Minecraft classes.
 * Implementations live in a selected Minecraft-version source set.
 */
public interface GameClientBridge {
    ClientReportSnapshot captureReportSnapshot(boolean includeEntities);

    Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance);

    Optional<Position3D> resolveEntityPosition(String entityId, String entityName, String dimensionId);

    boolean isEntityDead(String entityId);

    boolean isMiddleMouseButtonDown();

    void showActionBar(String message);
}
