package fun.prof_chen.teamviewer.integration.journeymap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fun.prof_chen.teamviewer.integration.journeymap.JourneyMapGroupPolicyBridge;
import journeymap.client.event.handlers.WaypointBeaconHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WaypointBeaconHandler.class)
abstract class WaypointBeaconHandlerMixin {
    @ModifyExpressionValue(
            method = "onRenderWaypoints(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Z)V",
            at = @At(value = "INVOKE", target = "Ljourneymap/common/properties/config/BooleanField;"
                    + "get()Ljava/lang/Boolean;", ordinal = 1), require = 0)
    private Boolean teamviewer$allowRelayWorldRendering(Boolean original) {
        return original || JourneyMapGroupPolicyBridge.anyWorldRenderingEnabled();
    }
}
