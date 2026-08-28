package fun.prof_chen.teamviewer.integration.journeymap.mixin;

import journeymap.client.render.draw.DrawWayPointStep;
import journeymap.client.waypoint.ClientWaypointImpl;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.geom.Point2D;

/** Keeps Relay player markers out of JourneyMap's costly offscreen-label layout. */
@Mixin(DrawWayPointStep.class)
abstract class DrawWayPointStepMixin {
    private static final String PROJECTION_KIND_KEY = "teamviewer.projection.kind";
    private static final String ONLINE_PLAYER_MARKER = "online-player-marker";
    private static final String OFFLINE_PLAYER_MARKER = "offline-player-marker";

    @Shadow @Final public ClientWaypointImpl waypoint;

    @Inject(method = "drawOffscreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void teamviewer$suppressRelayPlayerOffscreenLabels(
            GuiGraphicsExtractor graphics, Point2D point, double partialTicks, CallbackInfo callback) {
        String kind = waypoint.getCustomData(PROJECTION_KIND_KEY);
        if (ONLINE_PLAYER_MARKER.equals(kind) || OFFLINE_PLAYER_MARKER.equals(kind)) {
            callback.cancel();
        }
    }
}
