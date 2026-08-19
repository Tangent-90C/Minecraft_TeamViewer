package fun.prof_chen.teamviewer.api;

import java.util.List;

/** Immutable snapshot of authoritative last-seen player records visible to this client. */
public record LastSeenPlayerBatch(int apiVersion, boolean connected, List<LastSeenPlayerSnapshot> players) {
    public LastSeenPlayerBatch {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
