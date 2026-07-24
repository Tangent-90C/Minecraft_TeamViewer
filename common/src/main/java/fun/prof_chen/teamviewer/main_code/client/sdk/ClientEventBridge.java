package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.Set;

/** Registers all mandatory client lifecycle, input and rendering events exactly once. */
public interface ClientEventBridge<W, H> {
    void register(ClientEventHandler<W, H> handler);

    Set<ClientEventType> registeredEvents();
}
