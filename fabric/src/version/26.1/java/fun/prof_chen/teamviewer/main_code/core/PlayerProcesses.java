package fun.prof_chen.teamviewer.main_code.core;

import com.mojang.blaze3d.platform.InputConstants;
import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.ClientCoordinator;
import fun.prof_chen.teamviewer.main_code.client.ClientServices;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricGameClientBridge;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.config.FabricConfigLoader;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.bridge.WaypointSyncGateway;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero.XaeroWorldMapRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapRemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.sync.core.RemotePlayerProjectionCoordinator;
import fun.prof_chen.teamviewer.main_code.sync.core.SharedWaypointSyncCoordinator;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedSharedWaypointRepository;
import fun.prof_chen.teamviewer.main_code.network.bridge.FabricRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.transport.OkHttpTransportProcess;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

/** Minecraft 26.1 bootstrap. All reporting and connection policy stays in common. */
public final class PlayerProcesses implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("team-view-relay");
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("team-view-relay", "general"));

    private final Map<UUID, RemotePlayerInfo> remotePlayers = new ConcurrentHashMap<>();
    private final Map<String, SharedWaypointInfo> sharedWaypoints = new ConcurrentHashMap<>();
    private ClientCoordinator coordinator;
    private Config config;
    private NetworkManager networkManager;
    private RemotePlayerProjectionCoordinator remotePlayerProjectionCoordinator;
    private SharedWaypointSyncCoordinator sharedWaypointSyncCoordinator;
    private KeyMapping toggleKey;
    private KeyMapping configKey;
    private KeyMapping markKey;

    @Override
    public void onInitializeClient() {
        config = FabricConfigLoader.load();
        networkManager = new NetworkManager(
                remotePlayers, new FabricRuntimeGateway(), new OkHttpTransportProcess());
        NetworkManager.setConfigGateway(config);
        coordinator = new ClientCoordinator(config, networkManager, new FabricGameClientBridge());
        ClientServices.install(coordinator);
        remotePlayerProjectionCoordinator = new RemotePlayerProjectionCoordinator(
                java.util.List.of(
                        new XaeroWorldMapRemotePlayerProjection(),
                        new JourneyMapRemotePlayerProjection()));
        sharedWaypointSyncCoordinator = new SharedWaypointSyncCoordinator(
                new MapBackedSharedWaypointRepository(sharedWaypoints),
                new WaypointSyncGateway(networkManager),
                java.util.List.of());
        sharedWaypointSyncCoordinator.start();
        coordinator.configureWaypointSupport(
                new MapBackedSharedWaypointRepository(sharedWaypoints), sharedWaypointSyncCoordinator);

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_teamviewer.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_teamviewer.config", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY));
        markKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_teamviewer.mark", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            coordinator.onEndClientTick();
            collectRemotePlayerGizmos(client);
            remotePlayerProjectionCoordinator.tick(remotePlayers, coordinator.isEnabled());
            sharedWaypointSyncCoordinator.tick(coordinator.isEnabled(), config);
            while (toggleKey.consumeClick()) {
                coordinator.setEnabled(!coordinator.isEnabled());
                if (!coordinator.isEnabled()) {
                    remotePlayers.clear();
                    remotePlayerProjectionCoordinator.clear();
                    sharedWaypointSyncCoordinator.clear();
                }
            }
            while (configKey.consumeClick()) {
                client.setScreen(new ConfigScreen(client.screen));
            }
            while (markKey.consumeClick()) {
                if (client.screen == null) {
                    coordinator.createQuickMark();
                }
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (handler != null && !handler.getConnection().isMemoryConnection()) {
                coordinator.onJoinedMultiplayer();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            coordinator.onLeftPlaySession();
            remotePlayers.clear();
            remotePlayerProjectionCoordinator.clear();
            sharedWaypointSyncCoordinator.clear();
        });
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("team-view-relay", "network-status"),
                (graphics, deltaTracker) -> extractNetworkHud(graphics));
        LOGGER.info("TeamViewRelay initialized for Minecraft 26.1");
    }

    private void collectRemotePlayerGizmos(Minecraft client) {
        if (!coordinator.isEnabled() || client.player == null || client.level == null
                || (!config.isShowBoxes() && !config.isShowLines())) {
            return;
        }
        String dimension = client.level.dimension().identifier().toString();
        Vec3 local = client.player.getEyePosition();
        double maxDistanceSquared = (double) config.getRenderDistance() * config.getRenderDistance();
        for (RemotePlayerInfo remote : remotePlayers.values()) {
            if (remote == null || remote.position() == null || remote.uuid().equals(client.player.getUUID())
                    || (remote.dimension() != null && !remote.dimension().equals(dimension))) {
                continue;
            }
            double x = remote.position().x();
            double y = remote.position().y();
            double z = remote.position().z();
            Vec3 center = new Vec3(x, y + 0.9, z);
            if (local.distanceToSqr(center) > maxDistanceSquared) {
                continue;
            }
            if (config.isShowBoxes()) {
                Gizmos.cuboid(
                        new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3),
                        GizmoStyle.stroke(config.getBoxColor(), 2.0F));
            }
            if (config.isShowLines()) {
                Gizmos.line(local, center, config.getLineColor(), 2.0F);
            }
        }
    }

    private void extractNetworkHud(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        if (config == null || networkManager == null || !config.isShowNetworkTrafficHud()) {
            return;
        }
        NetworkManager.TrafficStatsSnapshot stats = networkManager.getTrafficStatsSnapshot();
        String stage = networkManager.getConnectionStage().name();
        String upload = "TV " + stage + "  ↑" + formatRate(stats.getUploadWireBytesPerSecond());
        String download = "↓" + formatRate(stats.getDownloadWireBytesPerSecond());
        Minecraft client = Minecraft.getInstance();
        int width = Math.max(client.font.width(upload), client.font.width(download)) + 8;
        graphics.fill(6, 6, 6 + width, 30, 0x88000000);
        graphics.text(client.font, upload, 10, 9, 0xFF7DD3FC, true);
        graphics.text(client.font, download, 10, 19, 0xFFA7F3D0, true);
    }

    private static String formatRate(long bytesPerSecond) {
        double value = Math.max(0L, bytesPerSecond);
        String[] units = {"B/s", "KiB/s", "MiB/s", "GiB/s"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.ROOT, value >= 100.0 ? "%.0f%s" : "%.1f%s", value, units[unit]);
    }
}
