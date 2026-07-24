package fun.prof_chen.teamviewer.main_code.mapbridge.model;

/** Native optional-map waypoint converted to block coordinates. */
public record NativeMapWaypointSnapshot(
        String nativeId,
        String name,
        String symbol,
        int x,
        int y,
        int z,
        String dimension,
        int color) { }
