package fun.prof_chen.teamviewer.api;

import java.util.Objects;

/** One remote player together with the effective local interaction decision. */
public record RemotePlayerView(RemotePlayerSnapshot player, PlayerInteractionState interaction) {
    public RemotePlayerView {
        Objects.requireNonNull(player, "player");
        interaction = interaction == null ? PlayerInteractionState.unresolved() : interaction;
    }
}
