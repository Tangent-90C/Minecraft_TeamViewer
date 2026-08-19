package fun.prof_chen.teamviewer.main_code.plugin;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.LastSeenPlayerInfo;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerRelationView;
import org.luaj.vm2.LuaValue;

import java.util.Map;
import java.util.UUID;

final class LuaRemotePlayerProjection implements RemotePlayerProjection {
    private final String id;
    private final LuaPluginRuntime runtime;
    private final LuaValue sync;
    private final LuaValue clear;
    private final LuaValue needsReconcile;
    private final LuaValue syncLastSeen;
    private final LuaCapabilityProbe probe;
    private volatile LuaCapabilityProbe.Result lastProbe;

    LuaRemotePlayerProjection(
            String id, LuaPluginRuntime runtime, LuaValue sync, LuaValue clear,
            LuaValue needsReconcile, LuaValue probe, LuaValue syncLastSeen) {
        this.id = id;
        this.runtime = runtime;
        this.sync = sync;
        this.clear = clear;
        this.needsReconcile = needsReconcile;
        this.syncLastSeen = syncLastSeen;
        this.probe = new LuaCapabilityProbe(id, runtime, probe);
    }

    @Override public String id() { return id; }
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
        runtime.invoke("remote.sync." + id, sync,
                LuaValueConverters.toLua(players), LuaValue.valueOf(enabled));
    }

    @Override
    public void syncResolved(
            Map<UUID, RemotePlayerInfo> players,
            Map<UUID, PlayerRelationView> relations,
            boolean enabled) {
        runtime.invoke("remote.sync." + id, sync,
                LuaValueConverters.toLua(players), LuaValue.valueOf(enabled),
                LuaValueConverters.toLua(relations));
    }

    @Override
    public void clear() {
        if (clear != null && clear.isfunction()) runtime.invoke("remote.clear." + id, clear);
        else RemotePlayerProjection.super.clear();
    }

    @Override
    public void syncLastSeen(Map<UUID, LastSeenPlayerInfo> players, boolean enabled) {
        if (syncLastSeen != null && syncLastSeen.isfunction()) {
            runtime.invoke("remote.sync_last_seen." + id, syncLastSeen,
                    LuaValueConverters.toLua(players), LuaValue.valueOf(enabled));
        }
    }

    @Override
    public boolean needsReconcile() {
        return needsReconcile != null && needsReconcile.isfunction()
                && runtime.invoke("remote.needs_reconcile." + id, needsReconcile).toboolean();
    }
}
