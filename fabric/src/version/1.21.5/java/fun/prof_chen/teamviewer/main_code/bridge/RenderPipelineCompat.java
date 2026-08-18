package fun.prof_chen.teamviewer.main_code.bridge;

import com.mojang.blaze3d.pipeline.RenderPipeline;

final class RenderPipelineCompat {
    private RenderPipelineCompat() { }

    static void addUniform(RenderPipeline.Builder builder, RenderPipeline.UniformDescription uniform) {
        if (uniform.type() != null) {
            builder.withUniform(uniform.name(), uniform.type());
        }
    }
}
