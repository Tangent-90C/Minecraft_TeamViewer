package net.minecraft.world;

/** Minimal dimension-key holder used by reflection tests. */
public final class World {
    public static final Key OVERWORLD = new Key("minecraft:overworld");

    private World() { }

    public record Key(String id) {
        @Override public String toString() { return id; }
    }
}
