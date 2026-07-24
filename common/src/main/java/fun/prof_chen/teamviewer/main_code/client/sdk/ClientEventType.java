package fun.prof_chen.teamviewer.main_code.client.sdk;

/** Mandatory semantic events every Minecraft adapter must register. */
public enum ClientEventType {
    END_CLIENT_TICK,
    TOGGLE_REQUESTED,
    CONFIG_REQUESTED,
    QUICK_MARK_REQUESTED,
    JOINED_MULTIPLAYER,
    LEFT_PLAY_SESSION,
    CLIENT_STOPPING,
    WORLD_RENDER,
    HUD_RENDER
}
