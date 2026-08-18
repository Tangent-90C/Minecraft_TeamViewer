package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import fun.prof_chen.teamviewer.main_code.render.FabricWorldRenderContext;
import net.minecraft.client.gui.DrawContext;

public final class FabricClientEventBridge extends AbstractFabricClientEventBridge<FabricWorldRenderContext> {
    @Override
    protected void registerWorldEvent(ClientEventHandler<FabricWorldRenderContext, DrawContext> handler) {
        FabricWorldRenderCallbackRegistry.register(handler::onWorldRender);
    }
}
