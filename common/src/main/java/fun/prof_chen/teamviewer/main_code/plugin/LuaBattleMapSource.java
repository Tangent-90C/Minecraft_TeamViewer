package fun.prof_chen.teamviewer.main_code.plugin;

import fun.prof_chen.teamviewer.main_code.client.sdk.BattleMapSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.BattleMapSourceSnapshot;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class LuaBattleMapSource implements BattleMapSource {
    private final String id;
    private final LuaPluginRuntime runtime;
    private final LuaValue capture;
    private final LuaCapabilityProbe probe;
    private volatile LuaCapabilityProbe.Result lastProbe;

    LuaBattleMapSource(String id, LuaPluginRuntime runtime, LuaValue capture, LuaValue probe) {
        this.id = id;
        this.runtime = runtime;
        this.capture = capture;
        this.probe = new LuaCapabilityProbe(id, runtime, probe);
    }

    @Override public String id() { return id; }
    @Override public fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus supportStatus() {
        lastProbe = probe.result();
        return lastProbe.status();
    }
    @Override public String supportDetail() {
        LuaCapabilityProbe.Result value = lastProbe;
        return value == null ? "" : value.detail();
    }

    @Override
    public Optional<BattleMapSourceSnapshot> capture() {
        LuaValue value = runtime.invoke("battle.capture." + id, capture);
        if (!value.istable()) return Optional.empty();
        List<BattleMapSourceSnapshot.Cell> cells = new ArrayList<>();
        LuaValue cellValues = value.get("cells");
        if (cellValues.istable()) {
            for (int index = 1; ; index++) {
                LuaValue cell = cellValues.get(index);
                if (cell.isnil()) break;
                cells.add(new BattleMapSourceSnapshot.Cell(
                        cell.get("x").optint(0), cell.get("z").optint(0),
                        cell.get("symbol").optjstring(""), cell.get("color").optjstring("#FFFFFF")));
            }
        }
        String coordinate = value.get("coordinateSpace").optjstring("relative_to_player")
                .toUpperCase(Locale.ROOT);
        BattleMapSourceSnapshot.CoordinateSpace coordinateSpace = coordinate.contains("ABSOLUTE")
                ? BattleMapSourceSnapshot.CoordinateSpace.ABSOLUTE_CHUNK
                : BattleMapSourceSnapshot.CoordinateSpace.RELATIVE_TO_PLAYER;
        return Optional.of(new BattleMapSourceSnapshot(id,
                value.get("dimension").optjstring("minecraft:overworld"),
                value.get("observedAt").optlong(System.currentTimeMillis()), coordinateSpace,
                value.get("mapSize").optint(0), value.get("anchorRow").optint(0),
                value.get("anchorColumn").optint(0), cells));
    }
}
