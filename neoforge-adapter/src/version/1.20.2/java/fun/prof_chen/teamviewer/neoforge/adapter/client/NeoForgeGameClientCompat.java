package fun.prof_chen.teamviewer.neoforge.adapter.client;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapObservationClock;
import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.neoforge.adapter.bridge.MinecraftDimensionAdapter;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

final class NeoForgeGameClientCompat {
    private static final Comparator<Score> SCORE_COMPARATOR = Comparator
            .comparingInt(Score::getScore).reversed()
            .thenComparing(Score::getOwner, String.CASE_INSENSITIVE_ORDER);

    private NeoForgeGameClientCompat() { }

    static int minY(ClientLevel level) { return level.getMinBuildHeight(); }
    static Position3D cameraPosition(Camera camera) { return position(camera.getPosition()); }
    static Position3D cameraForward(Camera camera) { return position(camera.getLookVector()); }
    static Position3D cameraUp(Camera camera) { return position(camera.getUpVector()); }
    static Predicate<Entity> hitPredicate() { return entity -> !entity.isSpectator() && entity.isPickable(); }
    static long windowHandle(Minecraft client) { return client.getWindow().getWindow(); }
    static String profileName(PlayerInfo entry) { return entry.getProfile().getName(); }
    static String profileId(PlayerInfo entry) {
        return entry.getProfile().getId() == null ? null : entry.getProfile().getId().toString();
    }

    static ScoreboardSnapshot captureScoreboardSnapshot(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return ScoreboardSnapshot.unavailable();
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return ScoreboardSnapshot.unavailable();
        List<Score> entries = new ArrayList<>(scoreboard.getPlayerScores(objective));
        entries.removeIf(entry -> entry.getOwner() == null || entry.getOwner().startsWith("#"));
        entries.sort(SCORE_COMPARATOR);
        List<ScoreboardSnapshot.Line> lines = new ArrayList<>();
        for (Score entry : entries) {
            String owner = entry.getOwner();
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

    private static Position3D position(Vec3 value) { return new Position3D(value.x, value.y, value.z); }
    private static Position3D position(Vector3f value) {
        return new Position3D(value.x(), value.y(), value.z());
    }
    private static String normalizeColor(Style style) {
        TextColor color = style == null ? null : style.getColor();
        return color == null ? "#FFFFFF" : color.toString();
    }
}
