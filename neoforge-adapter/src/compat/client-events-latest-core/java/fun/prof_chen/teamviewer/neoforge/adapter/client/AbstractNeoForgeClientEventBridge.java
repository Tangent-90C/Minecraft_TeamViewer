package fun.prof_chen.teamviewer.neoforge.adapter.client;

import fun.prof_chen.teamviewer.main_code.client.model.SystemChatMessageSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventType;
import fun.prof_chen.teamviewer.neoforge.NeoForgeClientContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared NeoForge lifecycle/input registration; versions bind key categories and world events. */
abstract class AbstractNeoForgeClientEventBridge<W> implements ClientEventBridge<W, GuiGraphics> {
    private final AtomicBoolean registered = new AtomicBoolean();
    private KeyMapping toggle;
    private KeyMapping config;
    private KeyMapping mark;

    @Override
    public final void register(ClientEventHandler<W, GuiGraphics> handler) {
        Objects.requireNonNull(handler, "handler");
        if (!registered.compareAndSet(false, true)) {
            throw new IllegalStateException("Client events already registered");
        }
        toggle = createKey("key.mc_teamviewer.toggle", GLFW.GLFW_KEY_UNKNOWN);
        config = createKey("key.mc_teamviewer.config", GLFW.GLFW_KEY_O);
        mark = createKey("key.mc_teamviewer.mark", GLFW.GLFW_KEY_UNKNOWN);
        NeoForgeClientContext.modBus().addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post ignored) -> onEndTick(handler));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            if (event.getConnection() != null && !event.getConnection().isMemoryConnection()) {
                handler.onJoinedMultiplayer();
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut ignored) -> handler.onLeftPlaySession());
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System event) ->
                handler.onSystemChatMessage(new SystemChatMessageSnapshot(
                        event.getMessage().getString(), event.isOverlay())));
        NeoForge.EVENT_BUS.addListener((ClientStoppingEvent ignored) -> handler.onClientStopping());
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> handler.onHudRender(event.getGuiGraphics()));
        registerWorldEvent(handler);
    }

    protected abstract KeyMapping createKey(String translationKey, int keyCode);

    protected abstract void registerWorldEvent(ClientEventHandler<W, GuiGraphics> handler);

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(toggle);
        event.register(config);
        event.register(mark);
    }

    private void onEndTick(ClientEventHandler<W, GuiGraphics> handler) {
        handler.onEndClientTick();
        while (toggle.consumeClick()) handler.onToggleRequested();
        while (config.consumeClick()) handler.onConfigRequested();
        while (mark.consumeClick()) {
            if (Minecraft.getInstance().screen == null) handler.onQuickMarkRequested();
        }
    }

    @Override
    public final Set<ClientEventType> registeredEvents() {
        return registered.get() ? Set.of(ClientEventType.values()) : Set.of();
    }
}
