package fun.prof_chen.teamviewer.main_code.client.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClientReportSnapshot(
        UUID localPlayerId,
        boolean localPlayerAlive,
        String dimension,
        List<PlayerSnapshot> players,
        List<EntitySnapshot> entities,
        List<TabPlayerSnapshot> tabPlayers) {
    public ClientReportSnapshot {
        players = List.copyOf(Objects.requireNonNullElse(players, List.of()));
        entities = List.copyOf(Objects.requireNonNullElse(entities, List.of()));
        tabPlayers = List.copyOf(Objects.requireNonNullElse(tabPlayers, List.of()));
    }

    public static ClientReportSnapshot unavailable() {
        return new ClientReportSnapshot(null, false, null, List.of(), List.of(), List.of());
    }
}
