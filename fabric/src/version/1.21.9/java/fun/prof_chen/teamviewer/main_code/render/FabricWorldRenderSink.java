package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.bridge.FabricWorldRenderBatchRenderer;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.core.WorldRenderBatchCompiler;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;

public final class FabricWorldRenderSink implements WorldRenderSink<FabricWorldRenderContext> {
    @Override
    public void render(FabricWorldRenderContext context, WorldRenderFrame frame) {
        if (context == null || context.matrices() == null) return;
        FabricWorldRenderBatchRenderer.render(
                context.matrices(), WorldRenderBatchCompiler.compile(frame));
    }
}
