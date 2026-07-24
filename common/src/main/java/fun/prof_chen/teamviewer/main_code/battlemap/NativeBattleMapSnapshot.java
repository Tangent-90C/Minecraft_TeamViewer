package fun.prof_chen.teamviewer.main_code.battlemap;

import java.util.List;

/** Absolute chunk cells supplied by optional native battle-map mods such as SimMC. */
public record NativeBattleMapSnapshot(String dimension, long observedAt, List<Cell> cells) {
    public NativeBattleMapSnapshot {
        cells = cells == null ? List.of() : List.copyOf(cells);
    }

    public record Cell(int chunkX, int chunkZ, String colorRaw, String symbol) { }
}
