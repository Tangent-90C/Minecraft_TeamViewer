package fun.prof_chen.teamviewer.main_code.battlemap;

import java.util.concurrent.atomic.AtomicLong;

public final class BattleMapSidebarSnapshotTracker {
    private static final AtomicLong SIDEBAR_REVISION = new AtomicLong();
    private static final AtomicLong LAST_SIDEBAR_OBSERVED_AT = new AtomicLong();

    private BattleMapSidebarSnapshotTracker() {
    }

    public static void markSidebarPacketObserved() {
        LAST_SIDEBAR_OBSERVED_AT.set(System.currentTimeMillis());
        SIDEBAR_REVISION.incrementAndGet();
    }

    public static long currentRevision() {
        return SIDEBAR_REVISION.get();
    }

    public static long lastSidebarObservedAt() {
        return LAST_SIDEBAR_OBSERVED_AT.get();
    }

    public static void reset() {
        SIDEBAR_REVISION.set(0L);
        LAST_SIDEBAR_OBSERVED_AT.set(0L);
    }
}
