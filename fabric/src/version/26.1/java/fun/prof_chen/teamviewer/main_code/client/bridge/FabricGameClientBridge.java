package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.bridge.MinecraftDimensionAdapter;
import fun.prof_chen.teamviewer.main_code.bridge.MinecraftPositionAdapter;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FabricGameClientBridge implements GameClientBridge {
    @Override
    public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return ClientReportSnapshot.unavailable();
        }
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
        List<EntitySnapshot> entities = new ArrayList<>();
        if (includeEntities) {
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
        }
        return new ClientReportSnapshot(
                client.player.getUUID(), client.player.isAlive(),
                MinecraftDimensionAdapter.toDimensionId(client.level.dimension()), players, entities, collectTabPlayers(client));
    }

    @Override
    public Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance) {
        Minecraft client = Minecraft.getInstance();
        if (client.hitResult == null || client.hitResult.getType() == HitResult.Type.MISS || client.player == null
                || client.hitResult.distanceTo(client.player) > maxDistance * maxDistance) {
            return Optional.empty();
        }
        if (!(client.hitResult instanceof EntityHitResult hit)) {
            return Optional.of(new EntityTargetSnapshot(
                    toPosition(client.hitResult.getLocation()), null, null, null, false, false));
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
        if (client.level == null || !MinecraftDimensionAdapter.toDimensionId(client.level.dimension()).equals(dimensionId)) {
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
        if (client.level == null) {
            return false;
        }
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
        return GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
    }

    @Override
    public void showActionBar(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.gui.setOverlayMessage(Component.literal(message), false);
        }
    }

    private static List<TabPlayerSnapshot> collectTabPlayers(Minecraft client) {
        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            return List.of();
        }
        List<TabPlayerSnapshot> result = new ArrayList<>();
        for (PlayerInfo entry : connection.getOnlinePlayers()) {
            if (entry == null || entry.getProfile() == null || entry.getProfile().name() == null) {
                continue;
            }
            String profileName = entry.getProfile().name();
            PlayerTeam team = entry.getTeam();
            if (team == null && client.level != null) {
                team = client.level.getScoreboard().getPlayersTeam(profileName);
            }
            result.add(new TabPlayerSnapshot(
                    entry.getProfile().id() == null ? null : entry.getProfile().id().toString(),
                    profileName,
                    team == null ? null : team.getName(),
                    team == null ? null : team.getPlayerPrefix().getString()));
        }
        return result;
    }

    private static Position3D toPosition(Vec3 position) {
        return MinecraftPositionAdapter.toPosition3D(position);
    }
}
