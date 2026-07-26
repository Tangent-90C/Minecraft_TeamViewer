package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Boundary for reads and commands that require Minecraft classes.
 * Implementations live in a selected Minecraft-version source set.
 */
public interface GameClientBridge {
    /** Hard upper bound for quick-mark raycasts; keeps native block traversal and entity queries bounded. */
    double MARK_TARGET_MAX_DISTANCE = 512.0D;

    /** Normalize a version adapter's native raycast range without allowing unbounded traversal. */
    static double normalizeMarkTargetDistance(double requestedDistance) {
        return Double.isFinite(requestedDistance) && requestedDistance > 0.0D
                ? Math.min(requestedDistance, MARK_TARGET_MAX_DISTANCE)
                : MARK_TARGET_MAX_DISTANCE;
    }

    ClientReportSnapshot captureReportSnapshot(boolean includeEntities);

    /** Capture the server player-list independently from movement/world snapshots. */
    List<TabPlayerSnapshot> captureTabPlayerSnapshot();

    /**
     * Capture camera/world state required by common rendering and sync decisions.
     * Implementations must not collect the server player-list here.
     */
    ClientWorldSnapshot captureWorldSnapshot(boolean includeEntities);

    /** Compatibility entrypoint used by trusted Lua snapshots that historically included entities. */
    default ClientWorldSnapshot captureWorldSnapshot() {
        return captureWorldSnapshot(true);
    }

    ScoreboardSnapshot captureScoreboardSnapshot();

    /**
     * Raycast blocks and entities from the camera up to {@code maxDistance} blocks.
     * An implementation may reuse a native prepared hit only when that Minecraft version computes it for the
     * requested range. Otherwise it must perform its own bounded raycast and defensively clamp invalid or
     * excessive distances to {@link #MARK_TARGET_MAX_DISTANCE}.
     */
    Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance);

    Optional<Position3D> resolveEntityPosition(String entityId, String entityName, String dimensionId);

    boolean isEntityDead(String entityId);

    boolean isMiddleMouseButtonDown();

    boolean isGameplayInputAvailable();

    void showActionBar(String message);
}
