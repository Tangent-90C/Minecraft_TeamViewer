package fun.prof_chen.teamviewer.main_code.network.bridge;

import fun.prof_chen.teamviewer.main_code.bridge.MinecraftDimensionAdapter;
import fun.prof_chen.teamviewer.main_code.config.TeamviewerModMetadata;
import fun.prof_chen.teamviewer.main_code.config.FabricModVersionProvider;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.UUID;

public final class FabricRuntimeGateway implements RuntimeGateway {
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
        return FabricModVersionProvider.getModVersion();
    }

    @Override
    public String getClientProtocolVersion() {
        return TeamviewerModMetadata.MetaProtocol.CLIENT_PROTOCOL_VERSION;
    }

    @Override
    public String getClientMinCompatibleProtocolVersion() {
        return TeamviewerModMetadata.MetaProtocol.CLIENT_MIN_COMPATIBLE_PROTOCOL_VERSION;
    }

    @Override
    public String getServerProtocolFallbackVersion() {
        return TeamviewerModMetadata.MetaProtocol.SERVER_PROTOCOL_VERSION_FALLBACK;
    }

    @Override
    public String getProgramVersionUnknown() {
        return TeamviewerModMetadata.PROGRAM_VERSION_UNKNOWN;
    }
}
