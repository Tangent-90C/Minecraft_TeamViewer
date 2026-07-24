package fun.prof_chen.teamviewer.main_code.client.sdk;

/** Callbacks consumed by a version event adapter. Render contexts remain opaque to common. */
public interface ClientEventHandler<W, H> {
    void onEndClientTick();
    void onToggleRequested();
    void onConfigRequested();
    void onQuickMarkRequested();
    void onJoinedMultiplayer();
    void onLeftPlaySession();
    void onClientStopping();
    void onWorldRender(W context);
    void onHudRender(H context);
}
