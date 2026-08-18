package fun.prof_chen.teamviewer.main_code.bridge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class FabricRenderLayerCompat {
    private static final Map<Double, RenderLayer> NO_DEPTH_LINES = new ConcurrentHashMap<>();
    private static volatile RenderLayer noDepthQuads;

    private FabricRenderLayerCompat() { }

    static void drawLines(BufferBuilder buffer, double width, boolean depthTest) {
        line(width, depthTest).draw(buffer, VertexSorter.BY_DISTANCE);
    }

    static void drawQuads(BufferBuilder buffer, boolean depthTest) {
        quads(depthTest).draw(buffer, VertexSorter.BY_DISTANCE);
    }

    private static RenderLayer line(double width, boolean depthTest) {
        if (depthTest) return RenderLayer.getDebugLineStrip(width);
        return NO_DEPTH_LINES.computeIfAbsent(width, value -> noDepth(
                RenderLayer.getDebugLineStrip(value), "teamviewer_no_depth_lines_" + sanitize(value)));
    }

    private static RenderLayer quads(boolean depthTest) {
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
                base.hasCrumbling(), false, () -> {
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
