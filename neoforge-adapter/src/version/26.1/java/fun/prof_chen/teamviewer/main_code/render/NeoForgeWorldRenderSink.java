package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** NeoForge event wrapper for the shared Minecraft 26.x Gizmo batch. */
public final class NeoForgeWorldRenderSink implements WorldRenderSink<RenderLevelStageEvent> {
    @Override
    public void render(RenderLevelStageEvent ignored, WorldRenderFrame frame) {
        MinecraftGizmoBatch.render(frame);
    }
}
