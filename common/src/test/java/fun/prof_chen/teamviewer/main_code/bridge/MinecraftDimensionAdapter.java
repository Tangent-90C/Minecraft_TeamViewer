package fun.prof_chen.teamviewer.main_code.bridge;

import net.minecraft.world.World;

/** Loader-specific dimension bridge test double. */
public final class MinecraftDimensionAdapter {
    private MinecraftDimensionAdapter() { }

    public static World.Key toRegistryKey(String id, World.Key fallback) {
        return id == null || id.isBlank() ? fallback : new World.Key(id);
    }
}
