package fun.prof_chen.teamviewer.main_code.model;

import java.util.Objects;

/** Structured battle-chunk identity shared by projection, transport and caches. */
public record BattleChunkRefData(String dimension, int chunkX, int chunkZ) implements Comparable<BattleChunkRefData> {
    public BattleChunkRefData {
        dimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (dimension.isEmpty()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
    }

    /** Private implementation identity; never expose this as public snapshot or patch state. */
    public String identityKey() {
        return dimension + "|" + chunkX + "|" + chunkZ;
    }

    @Override
    public int compareTo(BattleChunkRefData other) {
        int dimensionOrder = dimension.compareTo(other.dimension);
        if (dimensionOrder != 0) return dimensionOrder;
        int xOrder = Integer.compare(chunkX, other.chunkX);
        return xOrder != 0 ? xOrder : Integer.compare(chunkZ, other.chunkZ);
    }
}
