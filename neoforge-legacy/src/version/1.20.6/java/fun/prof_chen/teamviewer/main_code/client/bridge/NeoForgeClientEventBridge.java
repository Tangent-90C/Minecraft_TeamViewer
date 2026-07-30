package fun.prof_chen.teamviewer.neoforge.adapter.client;

import com.mojang.blaze3d.platform.InputConstants;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventType;
import fun.prof_chen.teamviewer.neoforge.NeoForgeClientContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Complete Minecraft 1.21.8 NeoForge event/input adapter. */
public final class NeoForgeClientEventBridge
        implements ClientEventBridge<RenderLevelStageEvent, GuiGraphics> {
    private final AtomicBoolean registered = new AtomicBoolean();
    private KeyMapping toggle;
    private KeyMapping config;
    private KeyMapping mark;

    @Override
    public void register(ClientEventHandler<RenderLevelStageEvent, GuiGraphics> handler) {
        Objects.requireNonNull(handler, "handler");
        if (!registered.compareAndSet(false, true)) throw new IllegalStateException("Client events already registered");
        toggle = new KeyMapping("key.mc_teamviewer.toggle", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), "category.mc_teamviewer.general");
        config = new KeyMapping("key.mc_teamviewer.config", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O, "category.mc_teamviewer.general");
        mark = new KeyMapping("key.mc_teamviewer.mark", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), "category.mc_teamviewer.general");
        NeoForgeClientContext.modBus().addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post ignored) -> onEndTick(handler));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            if (event.getConnection() != null && !event.getConnection().isMemoryConnection()) {
                handler.onJoinedMultiplayer();
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut ignored) -> handler.onLeftPlaySession());
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent ignored) -> handler.onClientStopping());
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> handler.onHudRender(event.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                handler.onWorldRender(event);
            }
        });
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(toggle);
        event.register(config);
        event.register(mark);
    }

    private void onEndTick(ClientEventHandler<RenderLevelStageEvent, GuiGraphics> handler) {
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
