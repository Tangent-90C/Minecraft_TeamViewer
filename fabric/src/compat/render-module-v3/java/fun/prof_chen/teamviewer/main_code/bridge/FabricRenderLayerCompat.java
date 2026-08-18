package fun.prof_chen.teamviewer.main_code.bridge;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class FabricRenderLayerCompat {
    private static final Map<Double, RenderLayer> NO_DEPTH_LINES = new ConcurrentHashMap<>();
    private static volatile RenderLayer noDepthQuads;
    private static final Method LAYER_FACTORY;
    private static final Field PIPELINE_FIELD;
    private static final Field PHASES_FIELD;

    static {
        try {
            Class<?> multiPhase = Class.forName("net.minecraft.client.render.RenderLayer$MultiPhase");
            Class<?> phases = Class.forName("net.minecraft.client.render.RenderLayer$MultiPhaseParameters");
            LAYER_FACTORY = RenderLayer.class.getDeclaredMethod(
                    "of", String.class, int.class, RenderPipeline.class, phases);
            LAYER_FACTORY.setAccessible(true);
            PIPELINE_FIELD = multiPhase.getDeclaredField("pipeline");
            PIPELINE_FIELD.setAccessible(true);
            PHASES_FIELD = multiPhase.getDeclaredField("phases");
            PHASES_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

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
        try {
            RenderPipeline pipeline = cloneWithoutDepth((RenderPipeline) PIPELINE_FIELD.get(base), name);
            return (RenderLayer) LAYER_FACTORY.invoke(
                    null, name, base.getExpectedBufferSize(), pipeline, PHASES_FIELD.get(base));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create no-depth render layer", exception);
        }
    }

    private static RenderPipeline cloneWithoutDepth(RenderPipeline base, String name) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(name)
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(base.getFragmentShader());
        Defines defines = base.getShaderDefines();
        if (defines != null && !defines.isEmpty()) {
            for (String flag : defines.flags()) builder.withShaderDefine(flag);
            for (Map.Entry<String, String> entry : defines.values().entrySet()) {
                addDefine(builder, entry.getKey(), entry.getValue());
            }
        }
        for (String sampler : base.getSamplers()) builder.withSampler(sampler);
        for (RenderPipeline.UniformDescription uniform : base.getUniforms()) {
            RenderPipelineCompat.addUniform(builder, uniform);
        }
        builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withPolygonMode(base.getPolygonMode()).withCull(base.isCull())
                .withColorWrite(base.isWriteColor(), base.isWriteAlpha())
                .withDepthWrite(base.isWriteDepth())
                .withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
                .withDepthBias(base.getDepthBiasScaleFactor(), base.getDepthBiasConstant());
        Optional<BlendFunction> blend = base.getBlendFunction();
        if (blend.isPresent()) builder.withBlend(blend.get()); else builder.withoutBlend();
        return builder.build();
    }

    private static void addDefine(RenderPipeline.Builder builder, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) return;
        try {
            builder.withShaderDefine(key, Integer.parseInt(value));
            return;
        } catch (NumberFormatException ignored) {
            // Try a floating-point define.
        }
        try {
            builder.withShaderDefine(key, Float.parseFloat(value));
        } catch (NumberFormatException ignored) {
            // Unsupported string defines are not accepted by this pipeline builder.
        }
    }

    private static String sanitize(double width) {
        return String.valueOf(width).replace('.', '_').replace('-', '_');
    }
}
