package fun.prof_chen.teamviewer.main_code.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.core.WorldRenderBatchCompiler;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderBatch;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Executes pre-1.21 world commands as a small set of grouped draw calls. */
public final class NeoForgeWorldRenderSink implements WorldRenderSink<RenderLevelStageEvent> {
    @Override
    public void render(RenderLevelStageEvent context, WorldRenderFrame frame) {
        if (context == null || frame == null || frame.cameraPosition() == null) return;
        PoseStack poseStack = context.getPoseStack();
        WorldRenderBatch batch = WorldRenderBatchCompiler.compile(frame);
        for (WorldRenderBatch.LineBatch lines : batch.lines()) drawLines(poseStack, lines);
        for (WorldRenderBatch.QuadBatch quads : batch.quads()) drawQuads(poseStack, quads);
    }

    private static void drawLines(PoseStack stack, WorldRenderBatch.LineBatch lines) {
        if (lines.vertices().isEmpty()) return;
        RenderSystem.lineWidth(lines.width());
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        addVertices(buffer, stack.last().pose(), lines.vertices());
        draw(buffer, lines.depthTest());
    }

    private static void drawQuads(PoseStack stack, WorldRenderBatch.QuadBatch quads) {
        if (quads.vertices().isEmpty()) return;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addVertices(buffer, stack.last().pose(), quads.vertices());
        draw(buffer, quads.depthTest());
    }

    private static void addVertices(BufferBuilder buffer, Matrix4f matrix,
                                    Iterable<WorldRenderBatch.Vertex> vertices) {
        for (WorldRenderBatch.Vertex vertex : vertices) {
            buffer.vertex(matrix, vertex.x(), vertex.y(), vertex.z())
                    .color(vertex.color()).endVertex();
        }
    }

    private static void draw(BufferBuilder buffer, boolean depthTest) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (depthTest) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
