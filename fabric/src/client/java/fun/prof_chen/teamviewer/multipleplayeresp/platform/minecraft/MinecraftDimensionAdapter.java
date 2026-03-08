package fun.prof_chen.teamviewer.multipleplayeresp.platform.minecraft;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class MinecraftDimensionAdapter {
	private MinecraftDimensionAdapter() {
	}

	public static String toDimensionId(RegistryKey<World> dimension) {
		if (dimension == null || dimension.getValue() == null) {
			return World.OVERWORLD.getValue().toString();
		}
		return dimension.getValue().toString();
	}

	public static RegistryKey<World> toRegistryKey(String dimensionId, RegistryKey<World> fallback) {
		if (dimensionId == null || dimensionId.isBlank()) {
			return fallback;
		}

		Identifier parsed = Identifier.tryParse(dimensionId);
		if (parsed == null) {
			return fallback;
		}

		return RegistryKey.of(RegistryKeys.WORLD, parsed);
	}
}