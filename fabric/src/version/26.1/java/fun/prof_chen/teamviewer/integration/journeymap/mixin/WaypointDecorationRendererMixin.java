package fun.prof_chen.teamviewer.integration.journeymap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fun.prof_chen.teamviewer.integration.journeymap.JourneyMapGroupPolicyBridge;
import journeymap.client.render.ingame.WaypointDecorationRenderer;
import journeymap.client.waypoint.ClientWaypointImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WaypointDecorationRenderer.class)
abstract class WaypointDecorationRendererMixin {
    @ModifyExpressionValue(method = "waypointsToDraw", at = @At(value = "INVOKE",
            target = "Ljourneymap/common/properties/config/BooleanField;get()Ljava/lang/Boolean;", ordinal = 1), require = 0)
    private Boolean teamviewer$filterRotatingBeam(
            Boolean original, @Local ClientWaypointImpl waypoint) {
        return JourneyMapGroupPolicyBridge.rotatingBeam(waypoint.getGroupId(), original);
    }

    @ModifyExpressionValue(method = "waypointsToDraw", at = @At(value = "INVOKE",
            target = "Ljourneymap/common/properties/config/BooleanField;get()Ljava/lang/Boolean;", ordinal = 2), require = 0)
    private Boolean teamviewer$filterStaticBeam(
            Boolean original, @Local ClientWaypointImpl waypoint) {
        return JourneyMapGroupPolicyBridge.staticBeam(waypoint.getGroupId(), original);
    }
}
