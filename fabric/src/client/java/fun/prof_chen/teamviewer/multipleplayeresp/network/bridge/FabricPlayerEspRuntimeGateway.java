package fun.prof_chen.teamviewer.multipleplayeresp.network.bridge;

import fun.prof_chen.teamviewer.multipleplayeresp.config.TeamviewerModMetadata;
import fun.prof_chen.teamviewer.multipleplayeresp.network.abstraction.PlayerEspRuntimeGateway;
import fun.prof_chen.teamviewer.multipleplayeresp.bridge.MinecraftDimensionAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.UUID;

public final class FabricPlayerEspRuntimeGateway implements PlayerEspRuntimeGateway {
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
    public String getClientProgramVersion() {
        return TeamviewerModMetadata.getModVersion();
    }

    @Override
    public String getClientProtocolVersion() {
        return TeamviewerModMetadata.PlayerEspProtocol.CLIENT_PROTOCOL_VERSION;
    }

    @Override
    public String getClientMinCompatibleProtocolVersion() {
        return TeamviewerModMetadata.PlayerEspProtocol.CLIENT_MIN_COMPATIBLE_PROTOCOL_VERSION;
    }

    @Override
    public String getServerProtocolFallbackVersion() {
        return TeamviewerModMetadata.PlayerEspProtocol.SERVER_PROTOCOL_VERSION_FALLBACK;
    }

    @Override
    public String getProgramVersionUnknown() {
        return TeamviewerModMetadata.PROGRAM_VERSION_UNKNOWN;
    }
}
