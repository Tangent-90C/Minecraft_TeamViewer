package fun.prof_chen.teamviewer.main_code.render;

import net.minecraft.client.util.math.MatrixStack;

/**
 * Minecraft 1.21.9 temporarily shipped without Fabric's world-render event API.
 * This context is supplied by the version-specific WorldRenderer mixin.
 */
public record FabricWorldRenderContext(MatrixStack matrices) {
}
