package fun.prof_chen.teamviewer.main_code.battlemap;

import java.util.concurrent.atomic.AtomicLong;

/** Platform-neutral scoreboard packet observation clock called by version Mixins. */
public final class BattleMapObservationClock {
    private static final AtomicLong LAST_OBSERVED_AT = new AtomicLong();

    private BattleMapObservationClock() { }

    public static void markChanged() {
        LAST_OBSERVED_AT.set(System.currentTimeMillis());
    }

    public static long lastObservedAt() {
        return LAST_OBSERVED_AT.get();
    }

    public static void reset() {
        LAST_OBSERVED_AT.set(0L);
    }
}
