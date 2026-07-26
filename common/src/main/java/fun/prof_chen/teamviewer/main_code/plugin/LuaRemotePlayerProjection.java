package fun.prof_chen.teamviewer.main_code.plugin;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import org.luaj.vm2.LuaValue;

import java.util.Map;
import java.util.UUID;

final class LuaRemotePlayerProjection implements RemotePlayerProjection {
    private final String id;
    private final Kind kind;
    private final LuaPluginRuntime runtime;
    private final LuaValue sync;
    private final LuaValue clear;
    private final LuaCapabilityProbe probe;
    private volatile LuaCapabilityProbe.Result lastProbe;

    LuaRemotePlayerProjection(
            String id, Kind kind, LuaPluginRuntime runtime, LuaValue sync, LuaValue clear, LuaValue probe) {
        this.id = id;
        this.kind = kind;
        this.runtime = runtime;
        this.sync = sync;
        this.clear = clear;
        this.probe = new LuaCapabilityProbe(runtime, probe);
    }

    @Override public String id() { return id; }
    @Override public Kind kind() { return kind; }
    @Override public boolean isAvailable() { return supportStatus() == fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus.AVAILABLE; }
    @Override public fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus supportStatus() {
        lastProbe = probe.result();
        return lastProbe.status();
    }
    @Override public String supportDetail() {
        LuaCapabilityProbe.Result value = lastProbe;
        return value == null ? "" : value.detail();
    }

    @Override
    public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
        runtime.invoke(sync, LuaValueConverters.toLua(players), LuaValue.valueOf(enabled));
    }

    @Override
    public void clear() {
        if (clear != null && clear.isfunction()) runtime.invoke(clear);
        else RemotePlayerProjection.super.clear();
    }
}
