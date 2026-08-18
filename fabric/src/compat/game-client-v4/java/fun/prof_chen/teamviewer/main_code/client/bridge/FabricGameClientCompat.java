package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

final class FabricGameClientCompat {
    private static final Method CAMERA_POSITION_METHOD = findCameraPositionMethod();
    private FabricGameClientCompat() { }

    static String entityTypeId(Entity entity) { return Registries.ENTITY_TYPE.getId(entity.getType()).toString(); }
    static Vec3d entityPosition(Entity entity) { return entity.getEntityPos(); }
    static String entityDimension(Entity entity) {
        return entity.getEntityWorld().getRegistryKey().getValue().toString();
    }
    static Vec3d cameraPosition(MinecraftClient client) {
        try {
            return (Vec3d) CAMERA_POSITION_METHOD.invoke(client.gameRenderer.getCamera());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read camera position", exception);
        }
    }
    static Predicate<Entity> hitPredicate() { return EntityPredicates.CAN_HIT; }
    static Text literal(String value) { return Text.literal(value); }
    static String profileName(PlayerListEntry entry) { return entry.getProfile().name(); }
    static String profileId(PlayerListEntry entry) {
        return entry.getProfile().id() == null ? null : entry.getProfile().id().toString();
    }
    static Team scoreboardTeam(Scoreboard scoreboard, String playerName) {
        return scoreboard.getScoreHolderTeam(playerName);
    }

    static ScoreboardSnapshot captureScoreboardSnapshot(MinecraftClient client) {
        return FabricModernScoreboardSupport.capture(client);
    }

    private static Method findCameraPositionMethod() {
        for (String methodName : List.of("getCameraPos", "getPos")) {
            try {
                return Camera.class.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                // Try the adjacent Minecraft API name.
            }
        }
        throw new ExceptionInInitializerError("Unsupported camera position API");
    }
}
