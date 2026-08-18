package fun.prof_chen.teamviewer.main_code.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;

final class NeoForgeRenderTypeCompat {
    private static final RenderType NO_DEPTH_LINES = createNoDepthLines();
    private static final RenderType NO_DEPTH_QUADS = createNoDepthQuads();

    private NeoForgeRenderTypeCompat() { }
    static RenderType noDepthLines() { return NO_DEPTH_LINES; }
    static RenderType noDepthQuads() { return NO_DEPTH_QUADS; }

    private static RenderType createNoDepthLines() {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation("teamviewer/pipeline/no_depth_lines")
                .withVertexShader("core/position_color").withFragmentShader("core/position_color")
                .withBlend(BlendFunction.TRANSLUCENT).withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES).build();
        return RenderType.create("teamviewer_no_depth_lines", 1536, false, false, pipeline,
                RenderType.CompositeState.builder().createCompositeState(false));
    }

    private static RenderType createNoDepthQuads() {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation("teamviewer/pipeline/no_depth_quads").withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build();
        return RenderType.create("teamviewer_no_depth_quads", 1536, false, true, pipeline,
                RenderType.CompositeState.builder().createCompositeState(false));
    }
}
