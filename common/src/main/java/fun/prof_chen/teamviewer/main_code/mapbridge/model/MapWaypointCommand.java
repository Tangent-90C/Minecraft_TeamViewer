package fun.prof_chen.teamviewer.main_code.mapbridge.model;

/** Immutable common command executed by a native map adapter. */
public record MapWaypointCommand(
        String waypointId,
        String name,
        String symbol,
        int x,
        int y,
        int z,
        String dimension,
        int color,
        String waypointKind,
        String tacticalType,
        String sourceType) {

    /** Source-compatible constructor for adapters that do not inspect waypoint provenance. */
    public MapWaypointCommand(
            String waypointId, String name, String symbol, int x, int y, int z, String dimension, int color) {
        this(waypointId, name, symbol, x, y, z, dimension, color, null, null, null);
    }
}
