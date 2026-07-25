package fun.prof_chen.teamviewer.neoforge.adapter.client;

import com.mojang.blaze3d.platform.InputConstants;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventType;
import fun.prof_chen.teamviewer.neoforge.NeoForgeClientContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Complete Minecraft 26.1 NeoForge event/input adapter. */
public final class NeoForgeClientEventBridge
        implements ClientEventBridge<RenderLevelStageEvent, GuiGraphicsExtractor> {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("team_view_relay", "general"));
    private final AtomicBoolean registered = new AtomicBoolean();
    private KeyMapping toggle;
    private KeyMapping config;
    private KeyMapping mark;

    @Override
    public void register(ClientEventHandler<RenderLevelStageEvent, GuiGraphicsExtractor> handler) {
        Objects.requireNonNull(handler, "handler");
        if (!registered.compareAndSet(false, true)) throw new IllegalStateException("Client events already registered");
        toggle = new KeyMapping("key.mc_teamviewer.toggle", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), CATEGORY);
        config = new KeyMapping("key.mc_teamviewer.config", InputConstants.Type.KEYSYM,
                InputConstants.KEY_O, CATEGORY);
        mark = new KeyMapping("key.mc_teamviewer.mark", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), CATEGORY);
        NeoForgeClientContext.modBus().addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post ignored) -> onEndTick(handler));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            if (event.getConnection() != null && !event.getConnection().isMemoryConnection()) {
                handler.onJoinedMultiplayer();
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut ignored) -> handler.onLeftPlaySession());
        NeoForge.EVENT_BUS.addListener((ClientStoppingEvent ignored) -> handler.onClientStopping());
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> handler.onHudRender(event.getGuiGraphics()));
        // Gizmos must be emitted before LevelRenderer finalizes its per-frame collector.
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterOpaqueFeatures event) -> handler.onWorldRender(event));
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(toggle);
        event.register(config);
        event.register(mark);
    }

    private void onEndTick(ClientEventHandler<RenderLevelStageEvent, GuiGraphicsExtractor> handler) {
        handler.onEndClientTick();
        while (toggle.consumeClick()) handler.onToggleRequested();
        while (config.consumeClick()) handler.onConfigRequested();
        while (mark.consumeClick()) {
            if (Minecraft.getInstance().screen == null) handler.onQuickMarkRequested();
        }
    }

    @Override
    public Set<ClientEventType> registeredEvents() {
        return registered.get() ? Set.of(ClientEventType.values()) : Set.of();
    }
}
