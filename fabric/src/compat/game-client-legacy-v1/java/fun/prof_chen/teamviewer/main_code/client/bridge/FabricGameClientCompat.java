package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.function.Predicate;

final class FabricGameClientCompat {
    private static final Function<String, Text> TEXT_FACTORY = createTextFactory();
    private FabricGameClientCompat() { }

    static String entityTypeId(Entity entity) { return Registry.ENTITY_TYPE.getId(entity.getType()).toString(); }
    static Vec3d entityPosition(Entity entity) { return entity.getPos(); }
    static String entityDimension(Entity entity) { return entity.getWorld().getRegistryKey().getValue().toString(); }
    static Vec3d cameraPosition(MinecraftClient client) { return client.gameRenderer.getCamera().getPos(); }
    static Predicate<Entity> hitPredicate() { return entity -> !entity.isSpectator(); }
    static Text literal(String value) { return TEXT_FACTORY.apply(value); }
    static String profileName(PlayerListEntry entry) { return entry.getProfile().getName(); }
    static String profileId(PlayerListEntry entry) {
        return entry.getProfile().getId() == null ? null : entry.getProfile().getId().toString();
    }
    static Team scoreboardTeam(Scoreboard scoreboard, String playerName) { return scoreboard.getPlayerTeam(playerName); }

    static ScoreboardSnapshot captureScoreboardSnapshot(MinecraftClient client) {
        return FabricLegacyScoreboardSupport.capture(client,
                scoreboard -> scoreboard.getObjectiveForSlot(Scoreboard.SIDEBAR_DISPLAY_SLOT_ID));
    }

    private static Function<String, Text> createTextFactory() {
        try {
            Method literal = Text.class.getMethod("literal", String.class);
            return value -> invokeTextFactory(literal, null, value);
        } catch (NoSuchMethodException ignored) {
            try {
                Constructor<?> constructor = Class.forName("net.minecraft.text.LiteralText")
                        .getConstructor(String.class);
                return value -> invokeTextFactory(constructor, value);
            } catch (ReflectiveOperationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    private static Text invokeTextFactory(Method method, Object receiver, String value) {
        try {
            return (Text) method.invoke(receiver, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create literal text", exception);
        }
    }

    private static Text invokeTextFactory(Constructor<?> constructor, String value) {
        try {
            return (Text) constructor.newInstance(value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create literal text", exception);
        }
    }
}
