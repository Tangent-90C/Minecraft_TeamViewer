package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.render.FabricWorldRenderContext;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Bridges the Minecraft 1.21.9 WorldRenderer mixin into the common client event port. */
public final class FabricWorldRenderCallbackRegistry {
    private static final AtomicReference<Consumer<FabricWorldRenderContext>> CALLBACK = new AtomicReference<>();

    private FabricWorldRenderCallbackRegistry() {
    }

    public static void register(Consumer<FabricWorldRenderContext> callback) {
        Objects.requireNonNull(callback, "callback");
        if (!CALLBACK.compareAndSet(null, callback)) {
            throw new IllegalStateException("World render callback already registered");
        }
    }

    public static void fire(FabricWorldRenderContext context) {
        Consumer<FabricWorldRenderContext> callback = CALLBACK.get();
        if (callback != null) callback.accept(context);
    }
}
