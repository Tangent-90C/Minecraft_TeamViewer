package fun.prof_chen.teamviewer.neoforge.adapter.client;

import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.function.Predicate;

final class NeoForgeGameClientCompat {
    private static final Method MIN_Y_METHOD = findMinYMethod();

    private NeoForgeGameClientCompat() { }

    static int minY(ClientLevel level) {
        try {
            return (int) MIN_Y_METHOD.invoke(level);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read minimum world height", exception);
        }
    }
    static Position3D cameraPosition(Camera camera) { return position(camera.getPosition()); }
    static Position3D cameraForward(Camera camera) { return position(camera.getLookVector()); }
    static Position3D cameraUp(Camera camera) { return position(camera.getUpVector()); }
    static Predicate<Entity> hitPredicate() { return EntitySelector.CAN_BE_COLLIDED_WITH; }
    static long windowHandle(Minecraft client) { return client.getWindow().getWindow(); }
    static String profileName(PlayerInfo entry) { return entry.getProfile().getName(); }
    static String profileId(PlayerInfo entry) {
        return entry.getProfile().getId() == null ? null : entry.getProfile().getId().toString();
    }
    static ScoreboardSnapshot captureScoreboardSnapshot(Minecraft client) {
        return NeoForgeScoreboardCompat.capture(client);
    }

    private static Position3D position(net.minecraft.world.phys.Vec3 value) {
        return new Position3D(value.x, value.y, value.z);
    }

    private static Position3D position(Vector3f value) {
        return new Position3D(value.x(), value.y(), value.z());
    }

    private static Method findMinYMethod() {
        for (String name : new String[]{"getMinY", "getMinBuildHeight"}) {
            try {
                return ClientLevel.class.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try the adjacent Minecraft API name.
            }
        }
        throw new ExceptionInInitializerError("Unsupported minimum world height API");
    }
}
