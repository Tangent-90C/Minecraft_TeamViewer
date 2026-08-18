package fun.prof_chen.teamviewer.neoforge.adapter.client;

import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.lang.reflect.Method;
import java.util.function.Predicate;

final class NeoForgeGameClientCompat {
    private static final Method CAMERA_POSITION = cameraMethod("position", "getPosition");
    private static final Method CAMERA_FORWARD = cameraMethod("forwardVector", "getLookVector");
    private static final Method CAMERA_UP = cameraMethod("upVector", "getUpVector");

    private NeoForgeGameClientCompat() { }

    static int minY(ClientLevel level) { return level.getMinY(); }
    static Position3D cameraPosition(Camera camera) { return position(invoke(CAMERA_POSITION, camera, Vec3.class)); }
    static Position3D cameraForward(Camera camera) { return position(invoke(CAMERA_FORWARD, camera, Vector3fc.class)); }
    static Position3D cameraUp(Camera camera) { return position(invoke(CAMERA_UP, camera, Vector3fc.class)); }
    static Predicate<Entity> hitPredicate() { return EntitySelector.CAN_BE_PICKED; }
    static long windowHandle(Minecraft client) { return client.getWindow().handle(); }
    static String profileName(PlayerInfo entry) { return entry.getProfile().name(); }
    static String profileId(PlayerInfo entry) {
        return entry.getProfile().id() == null ? null : entry.getProfile().id().toString();
    }
    static ScoreboardSnapshot captureScoreboardSnapshot(Minecraft client) {
        return NeoForgeScoreboardCompat.capture(client);
    }

    private static Position3D position(Vec3 value) { return new Position3D(value.x, value.y, value.z); }
    private static Position3D position(Vector3fc value) {
        return new Position3D(value.x(), value.y(), value.z());
    }

    private static Method cameraMethod(String... names) {
        for (String name : names) {
            try {
                return Camera.class.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try the adjacent Minecraft API name.
            }
        }
        throw new ExceptionInInitializerError("Unsupported camera API");
    }

    private static <T> T invoke(Method method, Camera camera, Class<T> resultType) {
        try {
            return resultType.cast(method.invoke(camera));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read camera state", exception);
        }
    }
}
