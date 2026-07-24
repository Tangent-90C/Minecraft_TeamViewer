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
        int color) { }
