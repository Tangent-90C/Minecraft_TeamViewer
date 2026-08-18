package fun.prof_chen.teamviewer.main_code.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderBatch;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared render-state and batching operations for the Fabric 1.18-1.19 family. */
final class FabricLegacyRenderCompat {
    private static final Map<Double, RenderLayer> DEPTH_LINES = new ConcurrentHashMap<>();
    private static final Map<Double, RenderLayer> NO_DEPTH_LINES = new ConcurrentHashMap<>();
    private static final RenderLayer DEPTH_QUADS = createQuads(true);
    private static final RenderLayer NO_DEPTH_QUADS = createQuads(false);

    private FabricLegacyRenderCompat() { }

    static void addLineVertex(BufferBuilder buffer, Object matrix, WorldRenderBatch.Vertex vertex,
                              float normalX, float normalY, float normalZ) {
        FabricLegacyApiCompat.addLineVertex(buffer, matrix, vertex, normalX, normalY, normalZ);
    }

    static void addQuadVertex(BufferBuilder buffer, Object matrix, WorldRenderBatch.Vertex vertex) {
        FabricLegacyApiCompat.addQuadVertex(buffer, matrix, vertex);
    }

    static void drawLines(BufferBuilder buffer, double width, boolean depthTest) {
        Map<Double, RenderLayer> cache = depthTest ? DEPTH_LINES : NO_DEPTH_LINES;
        cache.computeIfAbsent(width, value -> createLines(value, depthTest)).draw(buffer, 0, 0, 0);
    }

    static void drawQuads(BufferBuilder buffer, boolean depthTest) {
        (depthTest ? DEPTH_QUADS : NO_DEPTH_QUADS).draw(buffer, 0, 0, 0);
    }

    private static RenderLayer createLines(double width, boolean depthTest) {
        return new RenderLayer((depthTest ? "teamviewer_lines_" : "teamviewer_no_depth_lines_") + sanitize(width),
                VertexFormats.LINES, VertexFormat.DrawMode.LINES, 256, false, false, () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableCull();
                    RenderSystem.lineWidth((float) width);
                    FabricLegacyApiCompat.setLineShader();
                    if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                }, () -> {
                    RenderSystem.enableDepthTest();
                    RenderSystem.enableCull();
                    RenderSystem.disableBlend();
                }) { };
    }

    private static RenderLayer createQuads(boolean depthTest) {
        return new RenderLayer(depthTest ? "teamviewer_quads" : "teamviewer_no_depth_quads",
                VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 256, false, true, () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    FabricLegacyApiCompat.setQuadShader();
                    if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                }, () -> {
                    RenderSystem.enableDepthTest();
                    RenderSystem.disableBlend();
                }) { };
    }

    private static String sanitize(double width) {
        return String.valueOf(width).replace('.', '_').replace('-', '_');
    }
}
