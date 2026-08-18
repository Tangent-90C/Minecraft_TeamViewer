package fun.prof_chen.teamviewer.neoforge.adapter.client;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapObservationClock;
import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.neoforge.adapter.bridge.MinecraftDimensionAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class NeoForgeScoreboardCompat {
    private static final Comparator<PlayerScoreEntry> SCORE_COMPARATOR = Comparator
            .comparingInt(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private NeoForgeScoreboardCompat() { }

    static ScoreboardSnapshot capture(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return ScoreboardSnapshot.unavailable();
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return ScoreboardSnapshot.unavailable();
        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(SCORE_COMPARATOR);
        List<ScoreboardSnapshot.Line> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : entries) {
            String owner = entry.owner();
            PlayerTeam team = owner == null ? null : scoreboard.getPlayersTeam(owner);
            Component decorated = PlayerTeam.formatNameForTeam(team, Component.literal(owner == null ? "" : owner));
            List<ScoreboardSnapshot.Run> runs = new ArrayList<>();
            decorated.visit((style, text) -> {
                runs.add(new ScoreboardSnapshot.Run(text, normalizeColor(style)));
                return Optional.empty();
            }, Style.EMPTY);
            lines.add(new ScoreboardSnapshot.Line(decorated.getString(), runs));
        }
        return new ScoreboardSnapshot(MinecraftDimensionAdapter.toDimensionId(client.level.dimension()),
                BattleMapObservationClock.lastObservedAt(), lines);
    }

    private static String normalizeColor(Style style) {
        TextColor color = style == null ? null : style.getColor();
        return color == null ? "#FFFFFF" : color.toString();
    }
}
