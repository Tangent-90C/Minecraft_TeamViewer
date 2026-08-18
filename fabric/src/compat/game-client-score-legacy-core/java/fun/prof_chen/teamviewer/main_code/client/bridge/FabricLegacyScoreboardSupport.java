package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapObservationClock;
import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class FabricLegacyScoreboardSupport {
    private static final Comparator<ScoreboardPlayerScore> SCORE_COMPARATOR = Comparator
            .comparingInt(ScoreboardPlayerScore::getScore).reversed()
            .thenComparing(ScoreboardPlayerScore::getPlayerName, String.CASE_INSENSITIVE_ORDER);

    private FabricLegacyScoreboardSupport() { }

    static ScoreboardSnapshot capture(MinecraftClient client,
                                      Function<Scoreboard, ScoreboardObjective> sidebarObjective) {
        if (client.player == null || client.world == null) return ScoreboardSnapshot.unavailable();
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = sidebarObjective.apply(scoreboard);
        if (objective == null) return ScoreboardSnapshot.unavailable();
        List<ScoreboardPlayerScore> entries = new ArrayList<>(scoreboard.getAllPlayerScores(objective));
        entries.removeIf(entry -> entry.getPlayerName() == null || entry.getPlayerName().startsWith("#"));
        entries.sort(SCORE_COMPARATOR);
        List<ScoreboardSnapshot.Line> lines = new ArrayList<>();
        for (ScoreboardPlayerScore entry : entries) {
            String owner = entry.getPlayerName();
            Team team = owner == null ? null : scoreboard.getPlayerTeam(owner);
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
