package fun.prof_chen.teamviewer.main_code.battlemap;

import java.util.EnumMap;
import java.util.Map;

public final class BattleMapProviderRegistry {
    private final Map<BattleMapMode, BattleMapObservationProvider> providers = new EnumMap<>(BattleMapMode.class);

    public BattleMapProviderRegistry() {
        register(new NodeMcBattleMapObservationProvider());
        register(new SimMcBattleMapObservationProvider());
    }

    public BattleMapObservationProvider get(BattleMapMode mode) {
        BattleMapObservationProvider provider = providers.get(mode);
        if (provider != null) {
            return provider;
        }
        return providers.get(BattleMapMode.NODEMC);
    }

    public void resetAll() {
        for (BattleMapObservationProvider provider : providers.values()) {
            provider.reset();
        }
    }

    private void register(BattleMapObservationProvider provider) {
        providers.put(provider.mode(), provider);
    }
}
