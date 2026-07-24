package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Updates the capability report after the first real world and HUD render callbacks succeed. */
public final class AdapterRuntimeTck {
    private static final AtomicReference<AdapterTckReport> BASE_REPORT = new AtomicReference<>();
    private static final AtomicReference<Path> OUTPUT = new AtomicReference<>();
    private static final AtomicBoolean WORLD_RENDERED = new AtomicBoolean();
    private static final AtomicBoolean HUD_RENDERED = new AtomicBoolean();

    private AdapterRuntimeTck() { }

    public static void install(AdapterTckReport report, Path output) {
        BASE_REPORT.set(report);
        OUTPUT.set(output);
        WORLD_RENDERED.set(false);
        HUD_RENDERED.set(false);
        write();
    }

    public static void markWorldRenderSucceeded() {
        if (WORLD_RENDERED.compareAndSet(false, true)) write();
    }

    public static void markHudRenderSucceeded() {
        if (HUD_RENDERED.compareAndSet(false, true)) write();
    }

    public static void clear() {
        BASE_REPORT.set(null);
        OUTPUT.set(null);
        WORLD_RENDERED.set(false);
        HUD_RENDERED.set(false);
    }

    private static synchronized void write() {
        AdapterTckReport base = BASE_REPORT.get();
        Path output = OUTPUT.get();
        if (base == null || output == null) return;
        try {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(output,
                    base.withRenderObservations(WORLD_RENDERED.get(), HUD_RENDERED.get()).toJson(),
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Capability reporting must never crash the game client.
        }
    }
}
