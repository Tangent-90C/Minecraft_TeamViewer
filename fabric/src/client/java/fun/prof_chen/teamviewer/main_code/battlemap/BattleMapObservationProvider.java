package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.config.Config;
import net.minecraft.client.MinecraftClient;

import java.util.Optional;

public interface BattleMapObservationProvider {
    BattleMapMode mode();

    void tick(MinecraftClient client);

    Optional<BattleMapObservationResult> collect(MinecraftClient client, Config config);

    void reset();

    boolean isAvailable();

    default String unavailableReason() {
        return null;
    }
}
