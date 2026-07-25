package fun.prof_chen.teamviewer.main_code.network.bridge;

import fun.prof_chen.teamviewer.neoforge.adapter.bridge.MinecraftDimensionAdapter;
import fun.prof_chen.teamviewer.main_code.config.TeamviewerModMetadata;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.UUID;

public final class NeoForgeRuntimeGateway implements RuntimeGateway {
    @Override
    public String getCurrentDimensionId() {
        Minecraft client = Minecraft.getInstance();
        return MinecraftDimensionAdapter.toDimensionId(client.level == null ? Level.OVERWORLD : client.level.dimension());
    }
    @Override public UUID getLocalPlayerId() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? null : client.player.getUUID();
    }
    @Override public String getClientProgramVersion() {
        return ModList.get().getModContainerById("team_view_relay")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    }
    @Override public String getMinecraftVersion() { return SharedConstants.getCurrentVersion().name(); }
    @Override public String getClientProtocolVersion() { return TeamviewerModMetadata.MetaProtocol.CLIENT_PROTOCOL_VERSION; }
    @Override public String getClientMinCompatibleProtocolVersion() { return TeamviewerModMetadata.MetaProtocol.CLIENT_MIN_COMPATIBLE_PROTOCOL_VERSION; }
    @Override public String getServerProtocolFallbackVersion() { return TeamviewerModMetadata.MetaProtocol.SERVER_PROTOCOL_VERSION_FALLBACK; }
    @Override public String getProgramVersionUnknown() { return TeamviewerModMetadata.PROGRAM_VERSION_UNKNOWN; }
    @Override public Path getLogsDirectory() { return FMLPaths.GAMEDIR.get().resolve("logs"); }
    @Override public Path getConfigDirectory() { return FMLPaths.CONFIGDIR.get(); }
}
