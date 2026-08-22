package fun.prof_chen.teamviewer.integration.journeymap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fun.prof_chen.teamviewer.integration.journeymap.JourneyMapGroupPolicyBridge;
import journeymap.client.JourneymapClient;
import journeymap.client.render.ingame.WaypointRenderer;
import journeymap.client.waypoint.ClientWaypointImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaypointRenderer.class)
abstract class WaypointRendererMixin {
    @Inject(method = "canDrawWaypoint", at = @At("HEAD"), cancellable = true, require = 0)
    private void teamviewer$filterWorldRendering(
            ClientWaypointImpl waypoint, String playerDimension, CallbackInfoReturnable<Boolean> callback) {
        Boolean relay = JourneyMapGroupPolicyBridge.worldRendering(waypoint.getGroupId());
        boolean global = JourneymapClient.getInstance().getWaypointProperties().renderWaypointsWorld.get();
        if (Boolean.FALSE.equals(relay) || (relay == null && !global)) callback.setReturnValue(false);
    }

    @ModifyExpressionValue(
            method = "renderWaypoint(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Ljourneymap/client/waypoint/ClientWaypointImpl;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Ljourneymap/client/render/draw/DrawStep$Pass;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Ljourneymap/common/properties/config/IntegerField;"
                    + "get()Ljava/lang/Integer;", ordinal = 0), require = 0)
    private Integer teamviewer$overrideMaxDistance(
            Integer original, @Local(argsOnly = true) ClientWaypointImpl waypoint) {
        return JourneyMapGroupPolicyBridge.maxDistance(waypoint.getGroupId(), original);
    }
}
