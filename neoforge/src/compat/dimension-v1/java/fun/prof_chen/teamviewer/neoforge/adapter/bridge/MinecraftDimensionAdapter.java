package fun.prof_chen.teamviewer.neoforge.adapter.bridge;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

public final class MinecraftDimensionAdapter {
    private MinecraftDimensionAdapter() { }

    public static String toDimensionId(ResourceKey<Level> dimension) {
        return dimension == null ? "minecraft:overworld" : dimension.location().toString();
    }

    public static ResourceKey<Level> toResourceKey(String dimensionId, ResourceKey<Level> fallback) {
        ResourceLocation identifier = dimensionId == null ? null : ResourceLocation.tryParse(dimensionId);
        return identifier == null ? fallback : ResourceKey.create(Registries.DIMENSION, identifier);
    }
}
