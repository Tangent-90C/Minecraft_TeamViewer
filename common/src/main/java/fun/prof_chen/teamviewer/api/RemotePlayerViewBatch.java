package fun.prof_chen.teamviewer.api;

import java.util.List;

/** Immutable public view of remote players and relation metadata. */
public record RemotePlayerViewBatch(int apiVersion, boolean connected, List<RemotePlayerView> players) {
    public RemotePlayerViewBatch {
        players = List.copyOf(players == null ? List.of() : players);
    }
}
