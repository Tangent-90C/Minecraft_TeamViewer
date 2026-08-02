package fun.prof_chen.teamviewer.neoforge.adapter.bridge;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import net.minecraft.world.phys.Vec3;

public final class MinecraftPositionAdapter {
    private MinecraftPositionAdapter() { }

    public static Position3D toPosition3D(Vec3 value) {
        return new Position3D(value.x, value.y, value.z);
    }
}
