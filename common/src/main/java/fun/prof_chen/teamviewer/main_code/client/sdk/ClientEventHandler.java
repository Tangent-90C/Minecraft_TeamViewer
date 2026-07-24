package fun.prof_chen.teamviewer.main_code.client.sdk;

/** Callbacks consumed by a version event adapter. Render contexts remain opaque to common. */
public interface ClientEventHandler {
    void onEndClientTick();
    void onToggleRequested();
    void onConfigRequested();
    void onQuickMarkRequested();
    void onJoinedMultiplayer();
    void onLeftPlaySession();
    void onClientStopping();
    void onWorldRender(Object context);
    void onHudRender(Object context);
}
