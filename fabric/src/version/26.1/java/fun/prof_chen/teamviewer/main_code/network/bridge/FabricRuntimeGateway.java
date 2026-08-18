package fun.prof_chen.teamviewer.main_code.network.bridge;

import fun.prof_chen.teamviewer.minecraft.adapter.bridge.MinecraftDimensionAdapter;
import fun.prof_chen.teamviewer.main_code.plugin.MinecraftClientObjects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class FabricRuntimeGateway extends AbstractFabricRuntimeGateway {
    private static final MinecraftClientObjects CLIENT_OBJECTS = new MinecraftClientObjects() {
        @Override public Object blockPosition(int x, int y, int z) { return new BlockPos(x, y, z); }
        @Override public Object dimensionKey(String dimensionId) {
            return MinecraftDimensionAdapter.toResourceKey(dimensionId, Level.OVERWORLD);
        }
    };

    @Override
    protected MinecraftClientObjects clientObjects() {
        return CLIENT_OBJECTS;
    }
    @Override
    public String getCurrentDimensionId() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? "minecraft:overworld" : MinecraftDimensionAdapter.toDimensionId(client.level.dimension());
    }

    @Override
    public UUID getLocalPlayerId() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? null : client.player.getUUID();
    }

}
