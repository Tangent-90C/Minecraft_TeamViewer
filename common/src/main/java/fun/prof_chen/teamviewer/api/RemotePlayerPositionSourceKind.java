package fun.prof_chen.teamviewer.api;

/** Server-authenticated origin of a remote player's selected position. */
public enum RemotePlayerPositionSourceKind {
    UNKNOWN,
    SELF_REPORT,
    PLAYER_REPORT,
    EXTERNAL_SOURCE
}
