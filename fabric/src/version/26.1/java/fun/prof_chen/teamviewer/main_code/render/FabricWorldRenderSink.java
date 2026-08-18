package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/** Fabric event wrapper for the shared Minecraft 26.x Gizmo batch. */
public final class FabricWorldRenderSink implements WorldRenderSink<LevelRenderContext> {
    @Override
    public void render(LevelRenderContext ignored, WorldRenderFrame frame) {
        MinecraftGizmoBatch.render(frame);
    }
}
