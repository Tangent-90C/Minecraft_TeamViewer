package fun.prof_chen.teamviewer.main_code.client.model;

import fun.prof_chen.teamviewer.main_code.model.Position3D;

import java.util.List;
import java.util.UUID;

/** Complete immutable world/camera snapshot used by common render and HUD planners. */
public record ClientWorldSnapshot(
        UUID localPlayerId,
        String localPlayerName,
        boolean localPlayerAlive,
        String dimension,
        int worldBottomY,
        Position3D localPlayerPosition,
        Position3D cameraPosition,
        Position3D lookDirection,
        Position3D cameraUpDirection,
        List<PlayerSnapshot> players,
        List<EntitySnapshot> entities) {

    public ClientWorldSnapshot {
        players = players == null ? List.of() : List.copyOf(players);
        entities = entities == null ? List.of() : List.copyOf(entities);
    }

    public static ClientWorldSnapshot unavailable() {
        return new ClientWorldSnapshot(null, null, false, null, 0, null, null, null, null, List.of(), List.of());
    }

    public boolean available() {
        return localPlayerId != null && localPlayerPosition != null && cameraPosition != null && dimension != null;
    }
}
