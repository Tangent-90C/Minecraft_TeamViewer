package fun.prof_chen.teamviewer.neoforge.adapter.client;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapObservationClock;
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
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class NeoForgeGameClientBridge implements GameClientBridge {
    private static final Comparator<PlayerScoreEntry> SCORE_COMPARATOR = Comparator
            .comparingInt(PlayerScoreEntry::value).reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    @Override
    public ClientReportSnapshot captureReportSnapshot(boolean includeEntities) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return ClientReportSnapshot.unavailable();
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
                if (entity == client.player) continue;
                entities.add(new EntitySnapshot(
                        entity.getStringUUID(), toPosition(entity.position()), toPosition(entity.getDeltaMovement()),
                        MinecraftDimensionAdapter.toDimensionId(entity.level().dimension()), entity.getType().toString(),
                        entity.hasCustomName() ? entity.getDisplayName().getString() : null,
                        entity.getBbWidth(), entity.getBbHeight()));
            }
        }
        return new ClientReportSnapshot(
                client.player.getUUID(), client.player.isAlive(),
                MinecraftDimensionAdapter.toDimensionId(client.level.dimension()), players, entities,
                collectTabPlayers(client));
    }

    @Override
    public ClientWorldSnapshot captureWorldSnapshot() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return ClientWorldSnapshot.unavailable();
        ClientReportSnapshot report = captureReportSnapshot(true);
        Camera camera = client.gameRenderer.getMainCamera();
        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        return new ClientWorldSnapshot(
                report.localPlayerId(), client.player.getName().getString(), report.localPlayerAlive(), report.dimension(),
                client.level.getMinY(), toPosition(client.player.position()), toPosition(camera.getPosition()),
                new Position3D(forward.x(), forward.y(), forward.z()),
                new Position3D(up.x(), up.y(), up.z()), report.players(), report.entities());
    }

    @Override
    public ScoreboardSnapshot captureScoreboardSnapshot() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return ScoreboardSnapshot.unavailable();
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return ScoreboardSnapshot.unavailable();
        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(SCORE_COMPARATOR);
        List<ScoreboardSnapshot.Line> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : entries) {
            String owner = entry.owner();
            PlayerTeam team = owner == null ? null : scoreboard.getPlayersTeam(owner);
            Component decorated = PlayerTeam.formatNameForTeam(team, Component.literal(owner == null ? "" : owner));
            List<ScoreboardSnapshot.Run> runs = new ArrayList<>();
            decorated.visit((style, text) -> {
                runs.add(new ScoreboardSnapshot.Run(text, normalizeColor(style)));
                return Optional.empty();
            }, Style.EMPTY);
            lines.add(new ScoreboardSnapshot.Line(decorated.getString(), runs));
        }
        return new ScoreboardSnapshot(MinecraftDimensionAdapter.toDimensionId(client.level.dimension()),
                BattleMapObservationClock.lastObservedAt(), lines);
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
                cameraEntity, from, to, search, EntitySelector.CAN_BE_PICKED, nearestSquared);
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
        return GLFW.glfwGetMouseButton(client.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
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
        List<TabPlayerSnapshot> result = new ArrayList<>();
        for (PlayerInfo entry : connection.getOnlinePlayers()) {
            if (entry == null || entry.getProfile() == null || entry.getProfile().getName() == null) continue;
            String name = entry.getProfile().getName();
            PlayerTeam team = entry.getTeam();
            if (team == null && client.level != null) team = client.level.getScoreboard().getPlayersTeam(name);
            result.add(new TabPlayerSnapshot(
                    entry.getProfile().getId() == null ? null : entry.getProfile().getId().toString(), name,
                    team == null ? null : team.getName(),
                    team == null ? null : team.getPlayerPrefix().getString()));
        }
        return result;
    }

    private static Position3D toPosition(Vec3 value) {
        return MinecraftPositionAdapter.toPosition3D(value);
    }

    private static String normalizeColor(Style style) {
        TextColor color = style == null ? null : style.getColor();
        return color == null ? "#FFFFFF" : color.toString();
    }
}
