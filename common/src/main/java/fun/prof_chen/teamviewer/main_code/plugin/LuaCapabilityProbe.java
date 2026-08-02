package fun.prof_chen.teamviewer.main_code.plugin;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import org.luaj.vm2.LuaValue;

import java.util.Locale;

/** Dynamic support probe shared by all Lua-backed capability roles. */
final class LuaCapabilityProbe {
    private final LuaPluginRuntime runtime;
    private final LuaValue callback;
    private final String callbackId;

    LuaCapabilityProbe(String capabilityId, LuaPluginRuntime runtime, LuaValue callback) {
        this.runtime = runtime;
        this.callback = callback == null ? LuaValue.NIL : callback;
        this.callbackId = "probe." + capabilityId;
    }

    Result result() {
        if (!callback.isfunction()) return new Result(IntegrationSupportStatus.AVAILABLE, "");
        LuaValue value = runtime.invoke(callbackId, callback);
        if (!value.istable()) {
            return new Result(IntegrationSupportStatus.FAILED, "Lua capability probe returned no status table");
        }
        String raw = value.get("status").optjstring("AVAILABLE").toUpperCase(Locale.ROOT);
        IntegrationSupportStatus status;
        try {
            status = IntegrationSupportStatus.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            status = IntegrationSupportStatus.FAILED;
        }
        return new Result(status, value.get("detail").optjstring(""));
    }

    record Result(IntegrationSupportStatus status, String detail) { }
}
