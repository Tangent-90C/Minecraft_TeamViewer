package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** Complete Minecraft 1.21.8 Fabric event and input adapter. */
public final class FabricClientEventBridge implements ClientEventBridge {
    @Override
    public void register(ClientEventHandler handler) {
        Objects.requireNonNull(handler, "handler");
        KeyBinding toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mc_teamviewer.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                "category.mc_teamviewer.general"));
        KeyBinding config = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mc_teamviewer.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O,
                "category.mc_teamviewer.general"));
        KeyBinding mark = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mc_teamviewer.mark", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                "category.mc_teamviewer.general"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handler.onEndClientTick();
            while (toggle.wasPressed()) handler.onToggleRequested();
            while (config.wasPressed()) handler.onConfigRequested();
            while (mark.wasPressed()) {
                if (client.currentScreen == null) handler.onQuickMarkRequested();
            }
        });
        ClientPlayConnectionEvents.JOIN.register((networkHandler, sender, client) -> {
            if (networkHandler != null && !networkHandler.getConnection().isLocal()) handler.onJoinedMultiplayer();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((networkHandler, client) -> handler.onLeftPlaySession());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> handler.onClientStopping());
        WorldRenderEvents.AFTER_ENTITIES.register(handler::onWorldRender);
        HudRenderCallback.EVENT.register((context, tickDelta) -> handler.onHudRender(context));
    }
}
