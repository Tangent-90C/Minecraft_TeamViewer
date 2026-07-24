package fun.prof_chen.teamviewer.main_code.battlemap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardBattleMapParserTest {
    @Test
    void parsesStyledSquareAndExcludesPlayerAnchor() {
        ScoreboardSnapshot snapshot = new ScoreboardSnapshot("minecraft:overworld", 1234L, List.of(
                line("-1 0 1", ""),
                line("◼◼◼", "#FF0000"),
                line("◼┼◼", "green"),
                line("◼◼◼", "#0000FF")));

        ScoreboardBattleMapParser.ParsedSnapshot parsed = new ScoreboardBattleMapParser().parse(snapshot).orElseThrow();
        assertEquals(3, parsed.size());
        assertEquals(1, parsed.anchorRow());
        assertEquals(1, parsed.anchorColumn());
        assertEquals(8, parsed.cells().size());
        assertEquals(1234L, parsed.observedAt());
        assertTrue(parsed.cells().stream().anyMatch(cell -> cell.relChunkX() == -1 && cell.relChunkZ() == -1
                && "#FF0000".equals(cell.colorRaw())));
    }

    private static ScoreboardSnapshot.Line line(String text, String color) {
        return new ScoreboardSnapshot.Line(text, List.of(new ScoreboardSnapshot.Run(text, color)));
    }
}
