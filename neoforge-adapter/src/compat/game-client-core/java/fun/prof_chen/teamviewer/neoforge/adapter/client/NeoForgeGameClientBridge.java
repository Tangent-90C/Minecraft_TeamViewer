package fun.prof_chen.teamviewer.neoforge.adapter.client;

import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.neoforge.adapter.bridge.MinecraftDimensionAdapter;
import fun.prof_chen.teamviewer.neoforge.adapter.bridge.MinecraftPositionAdapter;
import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.entity.EntityCaptureTarget;
import fun.prof_chen.teamviewer.main_code.client.entity.EntityUploadFilter;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class NeoForgeGameClientBridge implements GameClientBridge {
    private static final Map<Object, String> ENTITY_TYPE_IDS = new IdentityHashMap<>();
    @Override
    public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return ClientReportSnapshot.unavailable();
        return new ClientReportSnapshot(
                client.player.getUUID(), client.player.isAlive(),
                MinecraftDimensionAdapter.toDimensionId(client.level.dimension()),
                collectPlayers(client), includeEntities ? collectEntities(client) : List.of());
    }

    @Override
    public void captureEntityFrame(EntityCaptureTarget target, EntityUploadFilter filter) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            target.begin(null, "", 0);
            target.finish(0);
            return;
        }
        String dimension = MinecraftDimensionAdapter.toDimensionId(client.level.dimension());
        target.begin(client.player.getUUID(), dimension, 0);
        int scanned = 0;
        boolean filterByName = filter.needsNameForDecision();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            scanned++;
            String type = ENTITY_TYPE_IDS.computeIfAbsent(
                    entity.getType(), value -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            boolean hasCustomName = entity.hasCustomName();
            String customName = filterByName && hasCustomName
                    ? entity.getCustomName().getString() : null;
            if (!filter.allowsStableType(type, customName)) continue;
            if (customName == null && hasCustomName) customName = entity.getCustomName().getString();
            Vec3 position = entity.position();
            Vec3 velocity = entity.getDeltaMovement();
            target.accept(entity.getUUID(),
                    position.x, position.y, position.z,
                    velocity.x, velocity.y, velocity.z,
                    type, customName, entity.getBbWidth(), entity.getBbHeight());
        }
        target.finish(scanned);
    }

    @Override
    public List<TabPlayerSnapshot> captureTabPlayerSnapshot() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? List.of() : collectTabPlayers(client);
    }

    @Override
    public ClientWorldSnapshot captureWorldSnapshot(boolean includeEntities) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return ClientWorldSnapshot.unavailable();
        Camera camera = client.gameRenderer.getMainCamera();
        return new ClientWorldSnapshot(
                client.player.getUUID(), client.player.getName().getString(), client.player.isAlive(),
                MinecraftDimensionAdapter.toDimensionId(client.level.dimension()),
                NeoForgeGameClientCompat.minY(client.level), toPosition(client.player.position()),
                NeoForgeGameClientCompat.cameraPosition(camera), NeoForgeGameClientCompat.cameraForward(camera),
                NeoForgeGameClientCompat.cameraUp(camera), collectPlayers(client),
                includeEntities ? collectEntities(client) : List.of());
    }

    @Override
    public ScoreboardSnapshot captureScoreboardSnapshot() {
        return NeoForgeGameClientCompat.captureScoreboardSnapshot(Minecraft.getInstance());
    }

    @Override
    public Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return Optional.empty();
        Entity cameraEntity = client.getCameraEntity();
        if (client.player == null || client.level == null || cameraEntity == null) return Optional.empty();
        double distance = GameClientBridge.normalizeMarkTargetDistance(maxDistance);
        float partialTick = 1.0F;
        Vec3 from = cameraEntity.getEyePosition(partialTick);
        HitResult blockHit = cameraEntity.pick(distance, partialTick, false);
        double nearestSquared = distance * distance;
        if (blockHit.getType() != HitResult.Type.MISS) nearestSquared = from.distanceToSqr(blockHit.getLocation());
        Vec3 direction = cameraEntity.getViewVector(partialTick);
        double rayLength = Math.sqrt(nearestSquared);
        Vec3 to = from.add(direction.scale(rayLength));
        AABB search = cameraEntity.getBoundingBox().expandTowards(direction.scale(rayLength)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                cameraEntity, from, to, search, NeoForgeGameClientCompat.hitPredicate(), nearestSquared);
        HitResult target = entityHit != null
                && from.distanceToSqr(entityHit.getLocation()) < from.distanceToSqr(blockHit.getLocation())
                ? entityHit : blockHit;
        if (target.getType() == HitResult.Type.MISS) return Optional.empty();
        if (!(target instanceof EntityHitResult hit)) {
            return Optional.of(new EntityTargetSnapshot(toPosition(target.getLocation()), null, null, null, false, false));
        }
        Entity entity = hit.getEntity();
        return Optional.of(new EntityTargetSnapshot(
                toPosition(hit.getLocation()), entity.getStringUUID(), entity.getType().toString(),
                entity.getName().getString(), entity instanceof LivingEntity,
                entity instanceof LivingEntity living && living.isDeadOrDying()));
    }

    @Override
    public Optional<Position3D> resolveEntityPosition(String entityId, String entityName, String dimensionId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null
                || !MinecraftDimensionAdapter.toDimensionId(client.level.dimension()).equals(dimensionId)) {
            return Optional.empty();
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.getStringUUID().equals(entityId)
                    || (entityName != null && entity.getName().getString().equalsIgnoreCase(entityName))) {
                return Optional.of(toPosition(entity.position()));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isEntityDead(String entityId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return false;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.getStringUUID().equals(entityId) && entity instanceof LivingEntity living) {
                return living.isDeadOrDying();
            }
        }
        return false;
    }

    @Override
    public boolean isMiddleMouseButtonDown() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        return GLFW.glfwGetMouseButton(NeoForgeGameClientCompat.windowHandle(client), GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isGameplayInputAvailable() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.screen == null;
    }

    @Override
    public void showActionBar(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) client.gui.setOverlayMessage(Component.literal(message), false);
    }

    private static List<TabPlayerSnapshot> collectTabPlayers(Minecraft client) {
        ClientPacketListener connection = client.getConnection();
        if (connection == null) return List.of();
        var onlinePlayers = connection.getOnlinePlayers();
        List<TabPlayerSnapshot> result = new ArrayList<>(onlinePlayers.size());
        for (PlayerInfo entry : onlinePlayers) {
            if (entry == null || entry.getProfile() == null || NeoForgeGameClientCompat.profileName(entry) == null) continue;
            String name = NeoForgeGameClientCompat.profileName(entry);
            PlayerTeam team = entry.getTeam();
            if (team == null && client.level != null) team = client.level.getScoreboard().getPlayersTeam(name);
            result.add(new TabPlayerSnapshot(
                    NeoForgeGameClientCompat.profileId(entry), name,
                    team == null ? null : team.getPlayerPrefix().getString(),
                    team == null ? null : team.getPlayerPrefix().getString(),
                    team == null ? null : team.getName()));
        }
        return result;
    }

    private static List<PlayerSnapshot> collectPlayers(Minecraft client) {
        List<PlayerSnapshot> players = new ArrayList<>();
        for (AbstractClientPlayer player : client.level.players()) {
            Entity vehicle = player.getVehicle();
            boolean riding = vehicle != null
                    && String.valueOf(vehicle.getType()).toLowerCase(Locale.ROOT).contains("horse");
            players.add(new PlayerSnapshot(
                    player.getUUID(), toPosition(player.position()), toPosition(player.getDeltaMovement()),
                    MinecraftDimensionAdapter.toDimensionId(player.level().dimension()), player.getName().getString(),
                    player.getHealth(), player.getMaxHealth(), player.getArmorValue(), riding,
                    player.getBbWidth(), player.getBbHeight()));
        }
        return players;
    }

    private static List<EntitySnapshot> collectEntities(Minecraft client) {
        List<EntitySnapshot> entities = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) {
                continue;
            }
            entities.add(new EntitySnapshot(
                    entity.getStringUUID(), toPosition(entity.position()), toPosition(entity.getDeltaMovement()),
                    MinecraftDimensionAdapter.toDimensionId(entity.level().dimension()), entity.getType().toString(),
                    entity.hasCustomName() ? entity.getDisplayName().getString() : null,
                    entity.getBbWidth(), entity.getBbHeight()));
        }
        return entities;
    }

    private static Position3D toPosition(Vec3 value) {
        return MinecraftPositionAdapter.toPosition3D(value);
    }

}
