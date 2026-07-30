package fun.prof_chen.teamviewer.mixin.client;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapObservationClock;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreResetS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onScoreboardDisplay", at = @At("TAIL"))
    private void teamviewer$trackScoreboardDisplay(ScoreboardDisplayS2CPacket packet, CallbackInfo ci) {
        BattleMapObservationClock.markChanged();
    }

    @Inject(method = "onScoreboardObjectiveUpdate", at = @At("TAIL"))
    private void teamviewer$trackScoreboardObjectiveUpdate(ScoreboardObjectiveUpdateS2CPacket packet, CallbackInfo ci) {
        BattleMapObservationClock.markChanged();
    }

    @Inject(method = "onTeam", at = @At("TAIL"))
    private void teamviewer$trackTeamUpdate(TeamS2CPacket packet, CallbackInfo ci) {
        BattleMapObservationClock.markChanged();
    }

    @Inject(method = "onScoreboardScoreUpdate", at = @At("TAIL"))
    private void teamviewer$trackScoreboardScoreUpdate(ScoreboardScoreUpdateS2CPacket packet, CallbackInfo ci) {
        BattleMapObservationClock.markChanged();
    }

    @Inject(method = "onScoreboardScoreReset", at = @At("TAIL"))
    private void teamviewer$trackScoreboardScoreReset(ScoreboardScoreResetS2CPacket packet, CallbackInfo ci) {
        BattleMapObservationClock.markChanged();
    }
}
