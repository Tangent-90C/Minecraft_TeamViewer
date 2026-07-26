package net.minecraft.util.math;

/** Minimal mapped BlockPos test double used by the JourneyMap Lua adapter. */
public record BlockPos(int x, int y, int z) {
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}
