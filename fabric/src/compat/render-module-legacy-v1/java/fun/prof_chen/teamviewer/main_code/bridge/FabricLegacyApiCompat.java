package fun.prof_chen.teamviewer.main_code.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderBatch;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Matrix4f;

/** Matrix and shader API adapter for Fabric 1.18-1.19.2. */
final class FabricLegacyApiCompat {
    private FabricLegacyApiCompat() { }

    static void addLineVertex(BufferBuilder buffer, Object matrix, WorldRenderBatch.Vertex vertex,
                              float normalX, float normalY, float normalZ) {
        int color = vertex.color();
        buffer.vertex((Matrix4f) matrix, vertex.x(), vertex.y(), vertex.z()).color(
                ((color >>> 16) & 0xFF) / 255.0F, ((color >>> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, ((color >>> 24) & 0xFF) / 255.0F)
                .normal(normalX, normalY, normalZ).next();
    }

    static void addQuadVertex(BufferBuilder buffer, Object matrix, WorldRenderBatch.Vertex vertex) {
        int color = vertex.color();
        buffer.vertex((Matrix4f) matrix, vertex.x(), vertex.y(), vertex.z())
                .color(((color >>> 16) & 0xFF) / 255.0F, ((color >>> 8) & 0xFF) / 255.0F,
                        (color & 0xFF) / 255.0F, ((color >>> 24) & 0xFF) / 255.0F).next();
    }

    static void setLineShader() {
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesShader);
    }

    static void setQuadShader() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }
}
