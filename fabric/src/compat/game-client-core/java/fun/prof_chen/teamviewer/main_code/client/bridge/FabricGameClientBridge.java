package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.entity.EntityCaptureTarget;
import fun.prof_chen.teamviewer.main_code.client.entity.EntityUploadFilter;
import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FabricGameClientBridge implements GameClientBridge {
    @Override
    public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return ClientReportSnapshot.unavailable();
        }
        return new ClientReportSnapshot(
                client.player.getUuid(), client.player.isAlive(),
                client.world.getRegistryKey().getValue().toString(),
                collectPlayers(client), includeEntities ? collectEntities(client) : List.of());
    }

    @Override
    public void captureEntityFrame(EntityCaptureTarget target, EntityUploadFilter filter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            target.begin(null, "", 0);
            target.finish(0);
            return;
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        target.begin(client.player.getUuid(), dimension, 0);
        int scanned = 0;
        boolean filterByName = filter.needsNameForDecision();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            scanned++;
            String type = FabricGameClientCompat.entityTypeId(entity);
            boolean hasCustomName = entity.hasCustomName();
            String customName = filterByName && hasCustomName
                    ? entity.getCustomName().getString() : null;
            if (!filter.allowsStableType(type, customName)) continue;
            if (customName == null && hasCustomName) customName = entity.getCustomName().getString();
            Vec3d position = FabricGameClientCompat.entityPosition(entity);
            Vec3d velocity = entity.getVelocity();
            target.accept(entity.getUuid(),
                    position.x, position.y, position.z,
                    velocity.x, velocity.y, velocity.z,
                    type, customName, entity.getWidth(), entity.getHeight());
        }
        target.finish(scanned);
    }

    @Override
    public List<TabPlayerSnapshot> captureTabPlayerSnapshot() {
        return collectTabPlayers(MinecraftClient.getInstance());
    }

    @Override
    public ClientWorldSnapshot captureWorldSnapshot(boolean includeEntities) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return ClientWorldSnapshot.unavailable();
        }
        Vec3d look = client.player.getRotationVec(1.0F).normalize();
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1.0E-6) {
            right = new Vec3d(1, 0, 0);
        }
        Vec3d cameraUp = right.normalize().crossProduct(look).normalize();
        return new ClientWorldSnapshot(
                client.player.getUuid(), client.player.getName().getString(), client.player.isAlive(),
                client.world.getRegistryKey().getValue().toString(),
                client.world.getBottomY(), toPosition(FabricGameClientCompat.entityPosition(client.player)),
                toPosition(FabricGameClientCompat.cameraPosition(client)), toPosition(look), toPosition(cameraUp),
                collectPlayers(client), includeEntities ? collectEntities(client) : List.of());
    }

    @Override
    public ScoreboardSnapshot captureScoreboardSnapshot() {
        return FabricGameClientCompat.captureScoreboardSnapshot(MinecraftClient.getInstance());
    }

    @Override
    public Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        if (client.player == null || client.world == null || cameraEntity == null) {
            return Optional.empty();
        }
        double distance = GameClientBridge.normalizeMarkTargetDistance(maxDistance);
        float tickDelta = 1.0F;
        Vec3d from = cameraEntity.getCameraPosVec(tickDelta);
        HitResult blockHit = cameraEntity.raycast(distance, tickDelta, false);
        double nearestDistanceSquared = distance * distance;
        if (blockHit.getType() != HitResult.Type.MISS) {
            nearestDistanceSquared = from.squaredDistanceTo(blockHit.getPos());
        }

        Vec3d direction = cameraEntity.getRotationVec(tickDelta);
        double rayLength = Math.sqrt(nearestDistanceSquared);
        Vec3d to = from.add(direction.multiply(rayLength));
        Box searchArea = cameraEntity.getBoundingBox()
                .stretch(direction.multiply(rayLength))
                .expand(1.0D);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                cameraEntity, from, to, searchArea,
                FabricGameClientCompat.hitPredicate(), nearestDistanceSquared);
        HitResult target = entityHit != null
                && from.squaredDistanceTo(entityHit.getPos()) < from.squaredDistanceTo(blockHit.getPos())
                ? entityHit : blockHit;
        if (target.getType() == HitResult.Type.MISS) {
            return Optional.empty();
        }
        if (!(target instanceof EntityHitResult hit)) {
            return Optional.of(new EntityTargetSnapshot(
                    toPosition(target.getPos()), null, null, null, false, false));
        }
        Entity entity = hit.getEntity();
        return Optional.of(new EntityTargetSnapshot(
                toPosition(hit.getPos()), entity.getUuidAsString(), entity.getType().toString(),
                entity.getName().getString(), entity instanceof LivingEntity,
                entity instanceof LivingEntity living && (!living.isAlive() || living.isDead())));
    }

    @Override
    public Optional<Position3D> resolveEntityPosition(String entityId, String entityName, String dimensionId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !client.world.getRegistryKey().getValue().toString().equals(dimensionId)) {
            return Optional.empty();
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity.getUuidAsString().equals(entityId)
                    || (entityName != null && entity.getName().getString().equalsIgnoreCase(entityName))) {
                return Optional.of(toPosition(FabricGameClientCompat.entityPosition(entity)));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isEntityDead(String entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return false;
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity.getUuidAsString().equals(entityId) && entity instanceof LivingEntity living) {
                return !living.isAlive() || living.isDead();
            }
        }
        return false;
    }

    @Override
    public boolean isMiddleMouseButtonDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getWindow() != null
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isGameplayInputAvailable() {
        return MinecraftClient.getInstance().currentScreen == null;
    }

    @Override
    public void showActionBar(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(FabricGameClientCompat.literal(message), true);
        }
    }

    private static List<TabPlayerSnapshot> collectTabPlayers(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return List.of();
        }
        var playerList = handler.getPlayerList();
        List<TabPlayerSnapshot> result = new ArrayList<>(playerList.size());
        Scoreboard scoreboard = client.world == null ? null : client.world.getScoreboard();
        for (PlayerListEntry entry : playerList) {
            if (entry == null || entry.getProfile() == null || FabricGameClientCompat.profileName(entry) == null) {
                continue;
            }
            String profileName = FabricGameClientCompat.profileName(entry);
            Team team = entry.getScoreboardTeam();
            if (team == null && scoreboard != null) {
                team = FabricGameClientCompat.scoreboardTeam(scoreboard, profileName);
            }
            result.add(new TabPlayerSnapshot(
                    FabricGameClientCompat.profileId(entry),
                    profileName,
                    team == null ? null : team.getPrefix().getString(),
                    team == null ? null : team.getPrefix().getString(),
                    team == null ? null : team.getName()));
        }
        return result;
    }

    private static List<PlayerSnapshot> collectPlayers(MinecraftClient client) {
        List<PlayerSnapshot> players = new ArrayList<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            Entity vehicle = player.getVehicle();
            boolean riding = vehicle != null
                    && String.valueOf(vehicle.getType()).toLowerCase(Locale.ROOT).contains("horse");
            players.add(new PlayerSnapshot(
                    player.getUuid(), toPosition(FabricGameClientCompat.entityPosition(player)),
                    toPosition(player.getVelocity()), FabricGameClientCompat.entityDimension(player),
                    player.getName().getString(),
                    player.getHealth(), player.getMaxHealth(), player.getArmor(), riding,
                    player.getWidth(), player.getHeight()));
        }
        return players;
    }

    private static List<EntitySnapshot> collectEntities(MinecraftClient client) {
        List<EntitySnapshot> entities = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) {
                continue;
            }
            entities.add(new EntitySnapshot(
                    entity.getUuidAsString(), toPosition(FabricGameClientCompat.entityPosition(entity)),
                    toPosition(entity.getVelocity()), FabricGameClientCompat.entityDimension(entity),
                    entity.getType().toString(),
                    entity.hasCustomName() ? entity.getDisplayName().getString() : null,
                    entity.getWidth(), entity.getHeight()));
        }
        return entities;
    }

    private static Position3D toPosition(Vec3d position) {
        return new Position3D(position.x, position.y, position.z);
    }

}
