package fun.prof_chen.teamviewer.neoforge;

import net.neoforged.bus.api.IEventBus;

import java.util.Objects;

/** Early NeoForge-owned context consumed by the ServiceLoader-created event bridge. */
public final class NeoForgeClientContext {
    private static IEventBus modBus;

    private NeoForgeClientContext() { }

    public static synchronized void initialize(IEventBus bus) {
        if (modBus != null) throw new IllegalStateException("NeoForge client context already initialized");
        modBus = Objects.requireNonNull(bus, "bus");
    }

    public static synchronized IEventBus modBus() {
        if (modBus == null) throw new IllegalStateException("NeoForge client context is not initialized");
        return modBus;
    }
}
