package fun.prof_chen.teamviewer.mixin.client;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapObservationClock;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSetDisplayObjective", at = @At("TAIL"))
    private void teamviewer$display(ClientboundSetDisplayObjectivePacket packet, CallbackInfo ci) { mark(); }
    @Inject(method = "handleAddObjective", at = @At("TAIL"))
    private void teamviewer$objective(ClientboundSetObjectivePacket packet, CallbackInfo ci) { mark(); }
    @Inject(method = "handleSetPlayerTeamPacket", at = @At("TAIL"))
    private void teamviewer$team(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) { mark(); }
    @Inject(method = "handleSetScore", at = @At("TAIL"))
    private void teamviewer$score(ClientboundSetScorePacket packet, CallbackInfo ci) { mark(); }
    @Inject(method = "handleResetScore", at = @At("TAIL"))
    private void teamviewer$reset(ClientboundResetScorePacket packet, CallbackInfo ci) { mark(); }
    private static void mark() { BattleMapObservationClock.markChanged(); }
}
