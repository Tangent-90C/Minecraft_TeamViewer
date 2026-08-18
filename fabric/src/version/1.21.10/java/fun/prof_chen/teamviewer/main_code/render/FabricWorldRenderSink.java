package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.bridge.FabricWorldRenderBatchRenderer;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.core.WorldRenderBatchCompiler;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

public final class FabricWorldRenderSink implements WorldRenderSink<WorldRenderContext> {
    @Override
    public void render(WorldRenderContext context, WorldRenderFrame frame) {
        if (context == null || context.matrices() == null) return;
        FabricWorldRenderBatchRenderer.render(
                context.matrices(), WorldRenderBatchCompiler.compile(frame));
    }
}
