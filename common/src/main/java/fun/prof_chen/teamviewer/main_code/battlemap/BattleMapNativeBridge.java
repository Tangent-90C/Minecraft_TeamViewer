package fun.prof_chen.teamviewer.main_code.battlemap;

import java.util.Optional;

public interface BattleMapNativeBridge {
    boolean isAvailable();
    String unavailableReason();
    Optional<NativeBattleMapSnapshot> capture();
}
