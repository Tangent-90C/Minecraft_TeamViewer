package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;

public final class FabricClientEventBridge extends AbstractFabricClientEventBridge<WorldRenderContext> {
    @Override
    protected void registerWorldEvent(ClientEventHandler<WorldRenderContext, DrawContext> handler) {
        WorldRenderEvents.AFTER_ENTITIES.register(handler::onWorldRender);
    }
}
