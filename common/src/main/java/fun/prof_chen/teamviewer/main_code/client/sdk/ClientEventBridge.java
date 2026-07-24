package fun.prof_chen.teamviewer.main_code.client.sdk;

/** Registers all mandatory client lifecycle, input and rendering events exactly once. */
public interface ClientEventBridge {
    void register(ClientEventHandler handler);
}
