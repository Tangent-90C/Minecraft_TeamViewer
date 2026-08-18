package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;

public final class FabricClientEventBridge extends AbstractFabricClientEventBridge<WorldExtractionContext> {
    @Override
    protected void registerWorldEvent(ClientEventHandler<WorldExtractionContext, DrawContext> handler) {
        WorldRenderEvents.END_EXTRACTION.register(handler::onWorldRender);
    }
}
