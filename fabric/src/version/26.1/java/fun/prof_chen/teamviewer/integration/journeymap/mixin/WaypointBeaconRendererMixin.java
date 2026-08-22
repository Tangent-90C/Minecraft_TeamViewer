package fun.prof_chen.teamviewer.integration.journeymap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fun.prof_chen.teamviewer.integration.journeymap.JourneyMapGroupPolicyBridge;
import journeymap.client.render.ingame.WaypointBeaconRenderer;
import journeymap.client.waypoint.ClientWaypointImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WaypointBeaconRenderer.class)
abstract class WaypointBeaconRendererMixin {
    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "INVOKE",
            target = "Ljourneymap/common/properties/config/BooleanField;get()Ljava/lang/Boolean;", ordinal = 1), require = 0)
    private Boolean teamviewer$filterRotatingBeam(
            Boolean original, @Local ClientWaypointImpl waypoint) {
        return JourneyMapGroupPolicyBridge.rotatingBeam(waypoint.getGroupId(), original);
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "INVOKE",
            target = "Ljourneymap/common/properties/config/BooleanField;get()Ljava/lang/Boolean;", ordinal = 2), require = 0)
    private Boolean teamviewer$filterStaticBeam(
            Boolean original, @Local ClientWaypointImpl waypoint) {
        return JourneyMapGroupPolicyBridge.staticBeam(waypoint.getGroupId(), original);
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Ljourneymap/client/render/draw/DrawStep$Pass;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;Ljourneymap/client/waypoint/ClientWaypointImpl;"
            + "FJ[FFDDDLnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DDD)V",
            at = @At(value = "INVOKE", target = "Ljourneymap/common/properties/config/BooleanField;"
                    + "get()Ljava/lang/Boolean;", ordinal = 0), require = 0)
    private Boolean teamviewer$renderStaticBeam(
            Boolean original, @Local(argsOnly = true) ClientWaypointImpl waypoint) {
        return JourneyMapGroupPolicyBridge.staticBeam(waypoint.getGroupId(), original);
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Ljourneymap/client/render/draw/DrawStep$Pass;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;Ljourneymap/client/waypoint/ClientWaypointImpl;"
            + "FJ[FFDDDLnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DDD)V",
            at = @At(value = "INVOKE", target = "Ljourneymap/common/properties/config/BooleanField;"
                    + "get()Ljava/lang/Boolean;", ordinal = 1), require = 0)
    private Boolean teamviewer$renderRotatingBeam(
            Boolean original, @Local(argsOnly = true) ClientWaypointImpl waypoint) {
        return JourneyMapGroupPolicyBridge.rotatingBeam(waypoint.getGroupId(), original);
    }
}
