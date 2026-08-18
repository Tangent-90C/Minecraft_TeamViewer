package fun.prof_chen.teamviewer.main_code.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

final class NeoForgeRenderTypeCompat {
    private static final RenderType NO_DEPTH_LINES = create(false);
    private static final RenderType NO_DEPTH_QUADS = create(true);

    private NeoForgeRenderTypeCompat() { }
    static RenderType noDepthLines() { return NO_DEPTH_LINES; }
    static RenderType noDepthQuads() { return NO_DEPTH_QUADS; }

    private static RenderType create(boolean quads) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false);
        return RenderType.create(quads ? "teamviewer_no_depth_quads" : "teamviewer_no_depth_lines",
                DefaultVertexFormat.POSITION_COLOR, quads ? VertexFormat.Mode.QUADS : VertexFormat.Mode.DEBUG_LINES,
                1536, false, quads, state);
    }
}
