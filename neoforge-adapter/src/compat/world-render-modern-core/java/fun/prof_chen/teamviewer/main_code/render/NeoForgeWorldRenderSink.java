package fun.prof_chen.teamviewer.main_code.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.core.WorldRenderBatchCompiler;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderBatch;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Executes common world commands as grouped native draw calls. */
public final class NeoForgeWorldRenderSink implements WorldRenderSink<RenderLevelStageEvent> {
    @Override
    public void render(RenderLevelStageEvent context, WorldRenderFrame frame) {
        if (context == null || frame == null || frame.cameraPosition() == null) return;
        PoseStack poseStack = context.getPoseStack();
        WorldRenderBatch batch = WorldRenderBatchCompiler.compile(frame);
        for (WorldRenderBatch.LineBatch lines : batch.lines()) {
            drawLines(poseStack, lines);
        }
        for (WorldRenderBatch.QuadBatch quads : batch.quads()) {
            drawQuads(poseStack, quads);
        }
    }

    private static void drawLines(PoseStack stack, WorldRenderBatch.LineBatch lines) {
        if (lines.vertices().isEmpty()) return;
        RenderSystem.lineWidth(lines.width());
        Matrix4f matrix = stack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (WorldRenderBatch.Vertex vertex : lines.vertices()) {
            buffer.addVertex(matrix, vertex.x(), vertex.y(), vertex.z()).setColor(vertex.color());
        }
        (lines.depthTest() ? RenderType.debugLineStrip(lines.width())
                : NeoForgeRenderTypeCompat.noDepthLines()).draw(buffer.buildOrThrow());
    }

    private static void drawQuads(PoseStack stack, WorldRenderBatch.QuadBatch quads) {
        if (quads.vertices().isEmpty()) return;
        Matrix4f matrix = stack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (WorldRenderBatch.Vertex vertex : quads.vertices()) {
            buffer.addVertex(matrix, vertex.x(), vertex.y(), vertex.z()).setColor(vertex.color());
        }
        (quads.depthTest() ? RenderType.debugQuads()
                : NeoForgeRenderTypeCompat.noDepthQuads()).draw(buffer.buildOrThrow());
    }
}
