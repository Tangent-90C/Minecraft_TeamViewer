package fun.prof_chen.teamviewer.main_code.core;

import com.mojang.blaze3d.platform.InputConstants;
import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.ClientCoordinator;
import fun.prof_chen.teamviewer.main_code.client.ClientServices;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricGameClientBridge;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.config.FabricConfigLoader;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.network.bridge.FabricRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.transport.OkHttpTransportProcess;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Minecraft 26.1 bootstrap. All reporting and connection policy stays in common. */
public final class PlayerProcesses implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("team-view-relay");
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("team-view-relay", "general"));

    private final Map<UUID, RemotePlayerInfo> remotePlayers = new ConcurrentHashMap<>();
    private ClientCoordinator coordinator;
    private KeyMapping toggleKey;
    private KeyMapping configKey;

    @Override
    public void onInitializeClient() {
        Config config = FabricConfigLoader.load();
        NetworkManager networkManager = new NetworkManager(
                remotePlayers, new FabricRuntimeGateway(), new OkHttpTransportProcess());
        NetworkManager.setConfigGateway(config);
        coordinator = new ClientCoordinator(config, networkManager, new FabricGameClientBridge());
        ClientServices.install(coordinator);

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_teamviewer.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_teamviewer.config", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            coordinator.onEndClientTick();
            while (toggleKey.consumeClick()) {
                coordinator.setEnabled(!coordinator.isEnabled());
                if (!coordinator.isEnabled()) {
                    remotePlayers.clear();
                }
            }
            while (configKey.consumeClick()) {
                client.setScreen(new ConfigScreen(client.screen));
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
        });
        LOGGER.info("TeamViewRelay initialized for Minecraft 26.1");
    }
}
