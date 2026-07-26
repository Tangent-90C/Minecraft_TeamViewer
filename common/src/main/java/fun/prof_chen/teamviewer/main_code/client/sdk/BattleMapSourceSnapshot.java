package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.List;

/** Standard snapshot consumed by the common battle-map coordinator. */
public record BattleMapSourceSnapshot(
        String sourceId,
        String dimension,
        long observedAt,
        CoordinateSpace coordinateSpace,
        int mapSize,
        int anchorRow,
        int anchorColumn,
        List<Cell> cells) {
    public BattleMapSourceSnapshot {
        cells = List.copyOf(cells == null ? List.of() : cells);
    }

    public enum CoordinateSpace {
        RELATIVE_TO_PLAYER,
        ABSOLUTE_CHUNK
    }

    public record Cell(int chunkX, int chunkZ, String symbol, String colorRaw) { }
}
