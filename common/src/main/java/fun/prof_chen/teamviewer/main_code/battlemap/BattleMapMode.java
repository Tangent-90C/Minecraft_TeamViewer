package fun.prof_chen.teamviewer.main_code.battlemap;

public enum BattleMapMode {
    NODEMC("nodemc"),
    SIMMC("simmc");

    private final String id;

    BattleMapMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "screen.mc_teamviewer.config.battle_map_mode." + id;
    }

    public BattleMapMode next() {
        return switch (this) {
            case NODEMC -> SIMMC;
            case SIMMC -> NODEMC;
        };
    }

    public static BattleMapMode fromId(String raw) {
        if (raw != null) {
            String normalized = raw.trim().toLowerCase();
            for (BattleMapMode mode : values()) {
                if (mode.id.equals(normalized)) {
                    return mode;
                }
            }
        }
        return NODEMC;
    }
}
