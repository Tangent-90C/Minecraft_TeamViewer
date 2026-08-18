package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapObservationClock;
import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class FabricModernScoreboardSupport {
    private static final Comparator<ScoreboardEntry> SCORE_COMPARATOR = Comparator
            .comparingInt(ScoreboardEntry::value).reversed()
            .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private FabricModernScoreboardSupport() { }

    static ScoreboardSnapshot capture(MinecraftClient client) {
        if (client.player == null || client.world == null) return ScoreboardSnapshot.unavailable();
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return ScoreboardSnapshot.unavailable();
        List<ScoreboardEntry> entries = new ArrayList<>(scoreboard.getScoreboardEntries(objective));
        entries.removeIf(ScoreboardEntry::hidden);
        entries.sort(SCORE_COMPARATOR);
        List<ScoreboardSnapshot.Line> lines = new ArrayList<>();
        for (ScoreboardEntry entry : entries) {
            String owner = entry.owner();
            Team team = owner == null ? null : scoreboard.getScoreHolderTeam(owner);
            Text decorated = Team.decorateName(team, FabricGameClientCompat.literal(owner == null ? "" : owner));
            List<ScoreboardSnapshot.Run> runs = new ArrayList<>();
            decorated.visit((style, text) -> {
                runs.add(new ScoreboardSnapshot.Run(text, FabricScoreboardTextSupport.normalizeColor(style)));
                return Optional.empty();
            }, Style.EMPTY);
            lines.add(new ScoreboardSnapshot.Line(decorated.getString(), runs));
        }
        return new ScoreboardSnapshot(client.world.getRegistryKey().getValue().toString(),
                BattleMapObservationClock.lastObservedAt(), lines);
    }
}
