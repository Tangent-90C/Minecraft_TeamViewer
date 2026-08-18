package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

final class FabricGameClientCompat {
    private FabricGameClientCompat() { }

    static String entityTypeId(Entity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType()).toString();
    }

    static Vec3d entityPosition(Entity entity) {
        return entity.getPos();
    }

    static String entityDimension(Entity entity) {
        return entity.getWorld().getRegistryKey().getValue().toString();
    }

    static Vec3d cameraPosition(MinecraftClient client) {
        return client.gameRenderer.getCamera().getPos();
    }

    static Predicate<Entity> hitPredicate() {
        return entity -> !entity.isSpectator() && entity.canHit();
    }

    static Text literal(String value) {
        return Text.literal(value);
    }

    static String profileName(PlayerListEntry entry) {
        return entry.getProfile().getName();
    }

    static String profileId(PlayerListEntry entry) {
        return entry.getProfile().getId() == null ? null : entry.getProfile().getId().toString();
    }

    static Team scoreboardTeam(Scoreboard scoreboard, String playerName) {
        return scoreboard.getPlayerTeam(playerName);
    }

    static ScoreboardSnapshot captureScoreboardSnapshot(MinecraftClient client) {
        return FabricLegacyScoreboardSupport.capture(client,
                scoreboard -> scoreboard.getObjectiveForSlot(Scoreboard.SIDEBAR_DISPLAY_SLOT_ID));
    }
}
