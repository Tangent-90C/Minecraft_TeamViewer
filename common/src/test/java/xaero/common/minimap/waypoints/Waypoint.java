package xaero.common.minimap.waypoints;

import java.util.UUID;

/** Xaero Minimap waypoint test double. */
public final class Waypoint {
    private final String id = UUID.randomUUID().toString();
    private final int x;
    private final int y;
    private final int z;
    private final String name;
    private final String symbol;
    private final int color;
    private boolean yIncluded;

    public Waypoint(int x, int y, int z, String name, String symbol, int color) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
        this.symbol = symbol;
        this.color = color;
    }

    public String getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public int getColor() { return color; }
    public void setYIncluded(boolean value) { yIncluded = value; }
}
