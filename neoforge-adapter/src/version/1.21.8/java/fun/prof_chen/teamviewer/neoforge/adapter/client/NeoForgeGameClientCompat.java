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
import org.joml.Vector3f;

import java.util.function.Predicate;

final class NeoForgeGameClientCompat {
    private NeoForgeGameClientCompat() { }

    static int minY(ClientLevel level) { return level.getMinY(); }
    static Position3D cameraPosition(Camera camera) { return position(camera.getPosition()); }
    static Position3D cameraForward(Camera camera) { return position(camera.getLookVector()); }
    static Position3D cameraUp(Camera camera) { return position(camera.getUpVector()); }
    static Predicate<Entity> hitPredicate() { return EntitySelector.CAN_BE_PICKED; }
    static long windowHandle(Minecraft client) { return client.getWindow().getWindow(); }
    static String profileName(PlayerInfo entry) { return entry.getProfile().getName(); }
    static String profileId(PlayerInfo entry) {
        return entry.getProfile().getId() == null ? null : entry.getProfile().getId().toString();
    }
    static ScoreboardSnapshot captureScoreboardSnapshot(Minecraft client) {
        return NeoForgeScoreboardCompat.capture(client);
    }

    private static Position3D position(Vec3 value) { return new Position3D(value.x, value.y, value.z); }
    private static Position3D position(Vector3f value) {
        return new Position3D(value.x(), value.y(), value.z());
    }
}
