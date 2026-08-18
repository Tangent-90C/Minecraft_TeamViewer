package fun.prof_chen.teamviewer.main_code.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderBatch;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** Emits each compiled line/quad group with one legacy BufferBuilder submission. */
public final class FabricWorldRenderBatchRenderer {
    private FabricWorldRenderBatchRenderer() { }

    public static void render(MatrixStack matrices, WorldRenderBatch batch) {
        if (matrices == null || batch == null) return;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (WorldRenderBatch.LineBatch lines : batch.lines()) drawLines(matrix, lines);
        for (WorldRenderBatch.QuadBatch quads : batch.quads()) drawQuads(matrix, quads);
    }

    private static void drawLines(Matrix4f matrix, WorldRenderBatch.LineBatch lines) {
        if (lines.vertices().isEmpty()) return;
        RenderSystem.lineWidth(lines.width());
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (WorldRenderBatch.Vertex vertex : lines.vertices()) add(buffer, matrix, vertex);
        FabricRenderLayerCompat.drawLines(buffer, lines.width(), lines.depthTest());
    }

    private static void drawQuads(Matrix4f matrix, WorldRenderBatch.QuadBatch quads) {
        if (quads.vertices().isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (WorldRenderBatch.Vertex vertex : quads.vertices()) add(buffer, matrix, vertex);
        FabricRenderLayerCompat.drawQuads(buffer, quads.depthTest());
    }

    private static void add(BufferBuilder buffer, Matrix4f matrix, WorldRenderBatch.Vertex vertex) {
        int color = vertex.color();
        buffer.vertex(matrix, vertex.x(), vertex.y(), vertex.z()).color(
                ((color >>> 16) & 0xFF) / 255.0F, ((color >>> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, ((color >>> 24) & 0xFF) / 255.0F).next();
    }
}
