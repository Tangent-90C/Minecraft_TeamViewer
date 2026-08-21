package fun.prof_chen.teamviewer.main_code.network.bridge;

import fun.prof_chen.teamviewer.main_code.plugin.MinecraftClientObjects;
import fun.prof_chen.teamviewer.neoforge.adapter.bridge.MinecraftDimensionAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Minecraft object access shared by NeoForge 1.20.2-1.21.11. */
public final class NeoForgeRuntimeGateway extends AbstractNeoForgeRuntimeGateway {
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
        return MinecraftDimensionAdapter.toDimensionId(
                client.level == null ? Level.OVERWORLD : client.level.dimension());
    }

    @Override
    public UUID getLocalPlayerId() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? null : client.player.getUUID();
    }

    @Override
    public boolean copyTextToClipboard(String text) {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(text == null ? "" : text);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public String getMinecraftVersion() {
        return NeoForgeRuntimeCompat.minecraftVersion();
    }
}
