package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.model.ClientReportSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntitySnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.EntityTargetSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
        List<PlayerSnapshot> players = new ArrayList<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            Entity vehicle = player.getVehicle();
            boolean riding = vehicle != null
                    && String.valueOf(vehicle.getType()).toLowerCase(Locale.ROOT).contains("horse");
            players.add(new PlayerSnapshot(
                    player.getUuid(), toPosition(player.getPos()), toPosition(player.getVelocity()),
                    player.getWorld().getRegistryKey().getValue().toString(), player.getName().getString(),
                    player.getHealth(), player.getMaxHealth(), player.getArmor(), riding,
                    player.getWidth(), player.getHeight()));
        }
        List<EntitySnapshot> entities = new ArrayList<>();
        if (includeEntities) {
            for (Entity entity : client.world.getEntities()) {
                if (entity == client.player) {
                    continue;
                }
                entities.add(new EntitySnapshot(
                        entity.getUuidAsString(), toPosition(entity.getPos()), toPosition(entity.getVelocity()),
                        entity.getWorld().getRegistryKey().getValue().toString(), entity.getType().toString(),
                        entity.hasCustomName() ? entity.getDisplayName().getString() : null,
                        entity.getWidth(), entity.getHeight()));
            }
        }
        return new ClientReportSnapshot(
                client.player.getUuid(), client.player.isAlive(),
                client.world.getRegistryKey().getValue().toString(), players, entities, collectTabPlayers(client));
    }

    @Override
    public Optional<EntityTargetSnapshot> resolveMarkTarget(double maxDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.crosshairTarget == null || client.crosshairTarget.getType() == HitResult.Type.MISS
                || client.player == null || client.player.getEyePos().squaredDistanceTo(client.crosshairTarget.getPos()) > maxDistance * maxDistance) {
            return Optional.empty();
        }
        if (client.crosshairTarget instanceof BlockHitResult hit) {
            return Optional.of(new EntityTargetSnapshot(toPosition(hit.getPos()), null, null, null, false, false));
        }
        if (!(client.crosshairTarget instanceof EntityHitResult hit)) {
            return Optional.empty();
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
                return Optional.of(toPosition(entity.getPos()));
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
    public void showActionBar(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }

    private static List<TabPlayerSnapshot> collectTabPlayers(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return List.of();
        }
        List<TabPlayerSnapshot> result = new ArrayList<>();
        Scoreboard scoreboard = client.world == null ? null : client.world.getScoreboard();
        for (PlayerListEntry entry : handler.getPlayerList()) {
            if (entry == null || entry.getProfile() == null || entry.getProfile().getName() == null) {
                continue;
            }
            String profileName = entry.getProfile().getName();
            Team team = entry.getScoreboardTeam();
            if (team == null && scoreboard != null) {
                team = scoreboard.getScoreHolderTeam(profileName);
            }
            result.add(new TabPlayerSnapshot(
                    entry.getProfile().getId() == null ? null : entry.getProfile().getId().toString(),
                    profileName,
                    team == null ? null : team.getName(),
                    team == null ? null : team.getPrefix().toString()));
        }
        return result;
    }

    private static Position3D toPosition(Vec3d position) {
        return new Position3D(position.x, position.y, position.z);
    }
}
