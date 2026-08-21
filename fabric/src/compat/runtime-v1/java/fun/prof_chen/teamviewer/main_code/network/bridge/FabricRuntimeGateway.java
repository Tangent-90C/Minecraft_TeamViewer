package fun.prof_chen.teamviewer.main_code.network.bridge;

import fun.prof_chen.teamviewer.main_code.bridge.MinecraftDimensionAdapter;
import fun.prof_chen.teamviewer.main_code.plugin.MinecraftClientObjects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public final class FabricRuntimeGateway extends AbstractFabricRuntimeGateway {
    private static final MinecraftClientObjects CLIENT_OBJECTS = new MinecraftClientObjects() {
        @Override public Object blockPosition(int x, int y, int z) { return new BlockPos(x, y, z); }
        @Override public Object dimensionKey(String dimensionId) {
            return MinecraftDimensionAdapter.toRegistryKey(dimensionId, World.OVERWORLD);
        }
    };

    @Override
    protected MinecraftClientObjects clientObjects() {
        return CLIENT_OBJECTS;
    }
    @Override
    public String getCurrentDimensionId() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            return MinecraftDimensionAdapter.toDimensionId(client.world.getRegistryKey());
        }
        return MinecraftDimensionAdapter.toDimensionId(World.OVERWORLD);
    }

    @Override
    public UUID getLocalPlayerId() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? null : client.player.getUuid();
    }

    @Override
    public boolean copyTextToClipboard(String text) {
        try {
            MinecraftClient.getInstance().keyboard.setClipboard(text == null ? "" : text);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

}
