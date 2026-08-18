package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.model.SystemChatMessageSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared Fabric lifecycle/input registration; versions only bind their world event. */
abstract class AbstractFabricClientEventBridge<W> implements ClientEventBridge<W, DrawContext> {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(
            Identifier.of("team-view-relay", "general"));
    private final AtomicBoolean registered = new AtomicBoolean();

    @Override
    public final void register(ClientEventHandler<W, DrawContext> handler) {
        Objects.requireNonNull(handler, "handler");
        if (!registered.compareAndSet(false, true)) {
            throw new IllegalStateException("Client events already registered");
        }
        KeyBinding toggle = registerKey("key.mc_teamviewer.toggle", GLFW.GLFW_KEY_UNKNOWN);
        KeyBinding config = registerKey("key.mc_teamviewer.config", GLFW.GLFW_KEY_O);
        KeyBinding mark = registerKey("key.mc_teamviewer.mark", GLFW.GLFW_KEY_UNKNOWN);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handler.onEndClientTick();
            while (toggle.wasPressed()) handler.onToggleRequested();
            while (config.wasPressed()) handler.onConfigRequested();
            while (mark.wasPressed()) {
                if (client.currentScreen == null) handler.onQuickMarkRequested();
            }
        });
        ClientPlayConnectionEvents.JOIN.register((networkHandler, sender, client) -> {
            if (networkHandler != null && !networkHandler.getConnection().isLocal()) {
                handler.onJoinedMultiplayer();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((networkHandler, client) -> handler.onLeftPlaySession());
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                handler.onSystemChatMessage(new SystemChatMessageSnapshot(message.getString(), overlay)));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> handler.onClientStopping());
        HudRenderCallback.EVENT.register((context, tickDelta) -> handler.onHudRender(context));
        registerWorldEvent(handler);
    }

    protected abstract void registerWorldEvent(ClientEventHandler<W, DrawContext> handler);

    private static KeyBinding registerKey(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey, InputUtil.Type.KEYSYM, keyCode, CATEGORY));
    }

    @Override
    public final Set<ClientEventType> registeredEvents() {
        return registered.get() ? Set.of(ClientEventType.values()) : Set.of();
    }
}
