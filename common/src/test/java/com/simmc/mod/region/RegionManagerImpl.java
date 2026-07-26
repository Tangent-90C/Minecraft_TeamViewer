package com.simmc.mod.region;

import java.util.LinkedHashMap;
import java.util.Map;

/** Test double matching SimMC's reflected manager shape. */
public final class RegionManagerImpl {
    private final Map<Object, Region> chunkToRegion = new LinkedHashMap<>();

    public void put(Object position, Region region) {
        chunkToRegion.put(position, region);
    }
}
