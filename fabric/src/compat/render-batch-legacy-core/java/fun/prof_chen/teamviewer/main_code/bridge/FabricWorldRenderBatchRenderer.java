package fun.prof_chen.teamviewer.main_code.bridge;

import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderBatch;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

/** Batches legacy LINES vertices while preserving the normals required by their shader. */
public final class FabricWorldRenderBatchRenderer {
    private FabricWorldRenderBatchRenderer() { }

    public static void render(MatrixStack ignored, WorldRenderBatch batch) {
        if (batch == null) return;
        Object matrix = new MatrixStack().peek().getPositionMatrix();
        for (WorldRenderBatch.LineBatch lines : batch.lines()) drawLines(matrix, lines);
        for (WorldRenderBatch.QuadBatch quads : batch.quads()) drawQuads(matrix, quads);
    }

    private static void drawLines(Object matrix, WorldRenderBatch.LineBatch lines) {
        if (lines.vertices().isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (int index = 0; index + 1 < lines.vertices().size(); index += 2) {
            WorldRenderBatch.Vertex first = lines.vertices().get(index);
            WorldRenderBatch.Vertex second = lines.vertices().get(index + 1);
            float x = second.x() - first.x(), y = second.y() - first.y(), z = second.z() - first.z();
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (length <= 1.0E-6F) continue;
            x /= length; y /= length; z /= length;
            FabricLegacyRenderCompat.addLineVertex(buffer, matrix, first, x, y, z);
            FabricLegacyRenderCompat.addLineVertex(buffer, matrix, second, x, y, z);
        }
        FabricLegacyRenderCompat.drawLines(buffer, lines.width(), lines.depthTest());
    }

    private static void drawQuads(Object matrix, WorldRenderBatch.QuadBatch quads) {
        if (quads.vertices().isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (WorldRenderBatch.Vertex vertex : quads.vertices()) {
            FabricLegacyRenderCompat.addQuadVertex(buffer, matrix, vertex);
        }
        FabricLegacyRenderCompat.drawQuads(buffer, quads.depthTest());
    }
}
