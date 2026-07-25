package fun.prof_chen.teamviewer.neoforge.adapter.bridge;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class MinecraftDimensionAdapter {
    private MinecraftDimensionAdapter() { }

    public static String toDimensionId(ResourceKey<Level> dimension) {
        return dimension == null ? "minecraft:overworld" : dimension.location().toString();
    }
}
