package fun.prof_chen.teamviewer.api;

import java.util.List;

/** Immutable snapshot of the remote-player state visible to this client. */
public record RemotePlayerBatch(int apiVersion, boolean connected, List<RemotePlayerSnapshot> players) {
    public RemotePlayerBatch {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
