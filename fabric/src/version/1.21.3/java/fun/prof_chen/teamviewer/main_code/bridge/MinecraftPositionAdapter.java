package fun.prof_chen.teamviewer.main_code.bridge;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.util.math.Vec3d;

public final class MinecraftPositionAdapter {
	private MinecraftPositionAdapter() {
	}

	public static Position3D fromVec3d(Vec3d source) {
		if (source == null) {
			return null;
		}
		return new Position3D(source.x, source.y, source.z);
	}

	public static Vec3d toVec3d(Position3D source) {
		if (source == null) {
			return null;
		}
		return new Vec3d(source.x(), source.y(), source.z());
	}
}
