package fun.prof_chen.teamviewer.main_code.client.bridge;

import com.mojang.blaze3d.platform.InputConstants;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventType;
import fun.prof_chen.teamviewer.main_code.client.model.SystemChatMessageSnapshot;
import fun.prof_chen.teamviewer.main_code.bridge.MinecraftClientUiCompat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fabric event and input adapter. */
public final class FabricClientEventBridge implements ClientEventBridge<LevelRenderContext, GuiGraphicsExtractor> {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("team-view-relay", "general"));
    private final AtomicBoolean registered = new AtomicBoolean();

    @Override
    public void register(ClientEventHandler<LevelRenderContext, GuiGraphicsExtractor> handler) {
        Objects.requireNonNull(handler, "handler");
        if (!registered.compareAndSet(false, true)) throw new IllegalStateException("Client events already registered");
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_teamviewer.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
        KeyMapping config = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_teamviewer.config", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY));
        KeyMapping mark = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mc_teamviewer.mark", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handler.onEndClientTick();
            while (toggle.consumeClick()) handler.onToggleRequested();
            while (config.consumeClick()) handler.onConfigRequested();
            while (mark.consumeClick()) {
                if (MinecraftClientUiCompat.currentScreen(client) == null) handler.onQuickMarkRequested();
            }
        });
        ClientPlayConnectionEvents.JOIN.register((networkHandler, sender, client) -> {
            if (networkHandler != null && !networkHandler.getConnection().isMemoryConnection()) handler.onJoinedMultiplayer();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((networkHandler, client) -> handler.onLeftPlaySession());
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                handler.onSystemChatMessage(new SystemChatMessageSnapshot(message.getString(), overlay)));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> handler.onClientStopping());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("team-view-relay", "network-status"),
                (graphics, deltaTracker) -> handler.onHudRender(graphics));
        LevelRenderEvents.BEFORE_GIZMOS.register(handler::onWorldRender);
    }

    @Override
    public Set<ClientEventType> registeredEvents() {
        return registered.get() ? Set.of(ClientEventType.values()) : Set.of();
    }
}
