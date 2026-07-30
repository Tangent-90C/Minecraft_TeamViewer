package fun.prof_chen.teamviewer.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricWorldRenderCallbackRegistry;
import fun.prof_chen.teamviewer.main_code.render.FabricWorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores the world-render lifecycle hook absent from Fabric API for Minecraft 1.21.9. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void teamviewer$afterWorldRender(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            Matrix4f frustumMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo callbackInfo) {
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix);
        FabricWorldRenderCallbackRegistry.fire(new FabricWorldRenderContext(matrices));
    }
}
