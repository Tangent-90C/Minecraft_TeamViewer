package fun.prof_chen.teamviewer.main_code.client.sdk;

public enum IntegrationRole {
    REMOTE_PLAYER("remote-player"),
    SHARED_WAYPOINT("shared-waypoint"),
    BATTLE_MAP_SOURCE("battle-map-source"),
    PLAYER_RELATION("player-relation");

    private final String id;

    IntegrationRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static IntegrationRole fromId(String value) {
        for (IntegrationRole role : values()) {
            if (role.id.equals(value)) return role;
        }
        throw new IllegalArgumentException("Unsupported integration role: " + value);
    }
}
