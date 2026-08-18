package fun.prof_chen.teamviewer.main_code.plugin;

import fun.prof_chen.teamviewer.api.PlayerRelation;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.PlayerRelationClassifier;
import org.luaj.vm2.LuaValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class LuaPlayerRelationClassifier implements PlayerRelationClassifier {
    private final String id;
    private final LuaPluginRuntime runtime;
    private final LuaValue classify;
    private final LuaCapabilityProbe probe;
    private volatile LuaCapabilityProbe.Result lastProbe;

    LuaPlayerRelationClassifier(
            String id, LuaPluginRuntime runtime, LuaValue classify, LuaValue probe) {
        this.id = id;
        this.runtime = runtime;
        this.classify = classify;
        this.probe = new LuaCapabilityProbe(id, runtime, probe);
    }

    @Override public String id() { return id; }

    @Override
    public IntegrationSupportStatus supportStatus() {
        lastProbe = probe.result();
        return lastProbe.status();
    }

    @Override
    public String supportDetail() {
        LuaCapabilityProbe.Result value = lastProbe;
        return value == null ? "" : value.detail();
    }

    @Override
    public Map<UUID, PlayerRelation> classify(List<TabPlayerSnapshot> players) {
        LuaValue result = runtime.invoke(
                "player_relation.classify." + id, classify, LuaValueConverters.toLua(players));
        if (!result.istable()) return Map.of();
        Map<UUID, PlayerRelation> relations = new LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            var next = result.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            try {
                UUID playerId = UUID.fromString(key.tojstring().trim());
                PlayerRelation relation = PlayerRelation.valueOf(
                        next.arg(2).tojstring().trim().toUpperCase(Locale.ROOT));
                relations.put(playerId, relation);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Map.copyOf(relations);
    }
}
