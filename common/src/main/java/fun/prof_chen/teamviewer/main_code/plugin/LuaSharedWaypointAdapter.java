package fun.prof_chen.teamviewer.main_code.plugin;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;

final class LuaSharedWaypointAdapter implements SharedWaypointMapAdapter {
    private final String id;
    private final LuaPluginRuntime runtime;
    private final LuaValue listLocal;
    private final LuaValue upsert;
    private final LuaValue delete;
    private final LuaValue clear;
    private final LuaValue needsReconcile;
    private final LuaCapabilityProbe probe;
    private volatile LuaCapabilityProbe.Result lastProbe;

    LuaSharedWaypointAdapter(String id, LuaPluginRuntime runtime, LuaValue listLocal,
                             LuaValue upsert, LuaValue delete, LuaValue clear,
                             LuaValue needsReconcile, LuaValue probe) {
        this.id = id;
        this.runtime = runtime;
        this.listLocal = listLocal;
        this.upsert = upsert;
        this.delete = delete;
        this.clear = clear;
        this.needsReconcile = needsReconcile;
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
    public List<NativeMapWaypointSnapshot> listLocalWaypoints() {
        LuaValue values = runtime.invoke("waypoint.list." + id, listLocal);
        if (!values.istable()) return List.of();
        List<NativeMapWaypointSnapshot> result = new ArrayList<>();
        for (int index = 1; ; index++) {
            LuaValue item = values.get(index);
            if (item.isnil()) break;
            result.add(new NativeMapWaypointSnapshot(
                    item.get("nativeId").optjstring(id + ":" + index),
                    item.get("name").optjstring("Waypoint"), item.get("symbol").optjstring("W"),
                    item.get("x").optint(0), item.get("y").optint(0), item.get("z").optint(0),
                    item.get("dimension").optjstring(""), item.get("color").optint(0xFFFFFF)));
        }
        return List.copyOf(result);
    }

    @Override public void upsertRemoteWaypoint(MapWaypointCommand command) {
        runtime.invoke("waypoint.upsert." + id, upsert, LuaValueConverters.toLua(command));
    }
    @Override public void deleteRemoteWaypoint(String waypointId) {
        runtime.invoke("waypoint.delete." + id, delete, LuaValue.valueOf(waypointId));
    }
    @Override public void clearRemoteWaypoints() { runtime.invoke("waypoint.clear." + id, clear); }
    @Override public boolean needsReconcile() {
        return needsReconcile != null && needsReconcile.isfunction()
                && runtime.invoke("waypoint.needs_reconcile." + id, needsReconcile).toboolean();
    }
}
