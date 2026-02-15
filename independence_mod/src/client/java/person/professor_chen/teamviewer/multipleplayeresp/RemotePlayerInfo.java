package person.professor_chen.teamviewer.multipleplayeresp;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public record RemotePlayerInfo(UUID uuid, Vec3d position, RegistryKey<World> dimension, String name) {
	public static RegistryKey<World> parseDimension(String dimensionId, RegistryKey<World> fallback) {
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