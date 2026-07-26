package fun.prof_chen.teamviewer.main_code.plugin;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Read-only, copy-oriented snapshots exposed to trusted Lua integration plugins. */
public record PluginHostAccess(
        Supplier<?> world,
        Supplier<?> players,
        Supplier<?> waypoints,
        Supplier<?> scoreboard,
        Map<String, Supplier<?>> services) {
    public PluginHostAccess(
            Supplier<?> world, Supplier<?> players, Supplier<?> waypoints, Supplier<?> scoreboard) {
        this(world, players, waypoints, scoreboard, Map.of());
    }

    public PluginHostAccess {
        world = safe(world);
        players = safe(players);
        waypoints = safe(waypoints);
        scoreboard = safe(scoreboard);
        services = Map.copyOf(services == null ? Map.of() : services);
    }

    public static PluginHostAccess empty() {
        return new PluginHostAccess(() -> null, () -> null, () -> null, () -> null, Map.of());
    }

    Object snapshot(String name) {
        return switch (Objects.requireNonNullElse(name, "")) {
            case "world" -> world.get();
            case "players" -> players.get();
            case "waypoints" -> waypoints.get();
            case "scoreboard" -> scoreboard.get();
            default -> null;
        };
    }

    Object service(String name) {
        Supplier<?> supplier = services.get(name);
        return supplier == null ? null : supplier.get();
    }

    private static Supplier<?> safe(Supplier<?> supplier) {
        return supplier == null ? () -> null : supplier;
    }
}
