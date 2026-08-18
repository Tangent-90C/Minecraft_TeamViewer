package fun.prof_chen.teamviewer.main_code.config.ui;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** SDK registry used by optional native config-menu entrypoints. */
public final class ConfigUiSessions {
    private static final AtomicReference<Supplier<ClientUiSession>> FACTORY = new AtomicReference<>();

    private ConfigUiSessions() { }

    public static void install(Supplier<ClientUiSession> factory) {
        FACTORY.set(Objects.requireNonNull(factory, "factory"));
    }

    public static ClientUiSession create() {
        Supplier<ClientUiSession> factory = FACTORY.get();
        if (factory == null) throw new IllegalStateException("Configuration UI runtime is not initialized");
        return Objects.requireNonNull(factory.get(), "client UI session");
    }

    public static void clear() {
        FACTORY.set(null);
    }
}
