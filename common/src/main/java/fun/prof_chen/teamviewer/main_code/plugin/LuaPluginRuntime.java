package fun.prof_chen.teamviewer.main_code.plugin;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

final class LuaPluginRuntime {
    private final String pluginId;
    private final Globals globals;
    private final Logger logger;
    private final BiConsumer<String, String> suspension;
    private LuaValue onDisable = LuaValue.NIL;
    private LuaValue onEnable = LuaValue.NIL;
    private LuaValue onSettingsChanged = LuaValue.NIL;
    private final Map<String, Integer> consecutiveFailures = new HashMap<>();
    private boolean suspended;

    LuaPluginRuntime(String pluginId, Globals globals, Logger logger, BiConsumer<String, String> suspension) {
        this.pluginId = pluginId;
        this.globals = Objects.requireNonNull(globals, "globals");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.suspension = Objects.requireNonNull(suspension, "suspension");
    }

    Globals globals() {
        return globals;
    }

    void setOnDisable(LuaValue callback) {
        onDisable = callback == null ? LuaValue.NIL : callback;
    }

    void setOnEnable(LuaValue callback) {
        onEnable = callback == null ? LuaValue.NIL : callback;
    }

    void setOnSettingsChanged(LuaValue callback) {
        onSettingsChanged = callback == null ? LuaValue.NIL : callback;
    }

    LuaValue invoke(String callbackId, LuaValue function, LuaValue... arguments) {
        if (suspended || function == null || !function.isfunction()) return LuaValue.NIL;
        String normalizedCallbackId = callbackId == null || callbackId.isBlank() ? "callback" : callbackId;
        try {
            Varargs result = function.invoke(LuaValue.varargsOf(arguments));
            consecutiveFailures.remove(normalizedCallbackId);
            return result.arg1();
        } catch (Throwable error) {
            int failures = consecutiveFailures.merge(normalizedCallbackId, 1, Integer::sum);
            String detail = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            logger.error("Lua integration plugin {} callback {} failed ({}/3): {}",
                    pluginId, normalizedCallbackId, failures, detail);
            if (failures >= 3) {
                suspended = true;
                suspension.accept(pluginId, normalizedCallbackId + ": " + detail);
            }
            return LuaValue.NIL;
        }
    }

    void disable() {
        if (onDisable == null || !onDisable.isfunction()) return;
        try {
            // Suspension blocks ordinary callbacks, but lifecycle cleanup still gets one chance.
            onDisable.invoke(LuaValue.NONE);
        } catch (Throwable error) {
            logger.error("Lua integration plugin {} on_disable failed: {}: {}", pluginId,
                    error.getClass().getSimpleName(), String.valueOf(error.getMessage()));
        }
    }

    void enable() {
        invoke("lifecycle.enable", onEnable);
    }

    void settingsChanged(String key, Object value) {
        invoke("lifecycle.settings_changed", onSettingsChanged,
                LuaValue.valueOf(key), LuaValueConverters.toLua(value));
    }
}
