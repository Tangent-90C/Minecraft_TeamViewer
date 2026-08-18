package fun.prof_chen.teamviewer.main_code.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class FabricRenderLayerCompat {
    private static final Map<Double, RenderLayer> NO_DEPTH_LINES = new ConcurrentHashMap<>();
    private static volatile RenderLayer noDepthQuads;

    private FabricRenderLayerCompat() { }

    static BufferBuilder beginLines() {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    static BufferBuilder beginQuads() {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
    }

    static RenderLayer line(double width, boolean depthTest) {
        if (depthTest) return RenderLayer.getDebugLineStrip(width);
        return NO_DEPTH_LINES.computeIfAbsent(width, value -> noDepth(
                RenderLayer.getDebugLineStrip(value), "teamviewer_no_depth_lines_" + sanitize(value)));
    }

    static RenderLayer quads(boolean depthTest) {
        if (depthTest) return RenderLayer.getDebugQuads();
        RenderLayer result = noDepthQuads;
        if (result != null) return result;
        synchronized (FabricRenderLayerCompat.class) {
            if (noDepthQuads == null) {
                noDepthQuads = noDepth(RenderLayer.getDebugQuads(), "teamviewer_no_depth_quads");
            }
            return noDepthQuads;
        }
    }

    private static RenderLayer noDepth(RenderLayer base, String name) {
        return new RenderLayer(name, base.getVertexFormat(), base.getDrawMode(), base.getExpectedBufferSize(),
                base.hasCrumbling(), base.isTranslucent(), () -> {
                    base.startDrawing();
                    RenderSystem.disableDepthTest();
                }, () -> {
                    RenderSystem.enableDepthTest();
                    base.endDrawing();
                }) { };
    }

    private static String sanitize(double width) {
        return String.valueOf(width).replace('.', '_').replace('-', '_');
    }
}
