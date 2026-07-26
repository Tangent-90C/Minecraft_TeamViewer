package com.simmc.mod.region;

/** Test double matching SimMC's reflected region methods. */
public record Region(int color, boolean core) {
    public boolean isCore() { return core; }
}
