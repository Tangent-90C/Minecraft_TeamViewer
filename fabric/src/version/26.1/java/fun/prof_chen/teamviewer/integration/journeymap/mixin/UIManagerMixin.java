package fun.prof_chen.teamviewer.integration.journeymap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fun.prof_chen.teamviewer.integration.journeymap.JourneyMapGroupPolicyBridge;
import journeymap.client.ui.UIManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(UIManager.class)
abstract class UIManagerMixin {
    @ModifyExpressionValue(
            method = "drawWaypointDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Ljourneymap/common/properties/config/BooleanField;"
                    + "get()Ljava/lang/Boolean;", ordinal = 1), require = 0)
    private Boolean teamviewer$allowRelayWorldRendering(Boolean original) {
        return original || JourneyMapGroupPolicyBridge.anyWorldRenderingEnabled();
    }
}
