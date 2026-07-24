package fun.prof_chen.teamviewer.main_code.battlemap;

import java.util.List;

/** Ordered, already-decorated sidebar text extracted by a Minecraft adapter. */
public record ScoreboardSnapshot(String dimension, long observedAt, List<Line> lines) {
    public ScoreboardSnapshot {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public static ScoreboardSnapshot unavailable() {
        return new ScoreboardSnapshot(null, 0L, List.of());
    }

    public record Line(String rawText, List<Run> runs) {
        public Line {
            runs = runs == null ? List.of() : List.copyOf(runs);
        }
    }

    public record Run(String text, String colorRaw) { }
}
