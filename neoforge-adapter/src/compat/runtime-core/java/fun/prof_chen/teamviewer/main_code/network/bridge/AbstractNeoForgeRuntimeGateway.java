package fun.prof_chen.teamviewer.main_code.network.bridge;

import fun.prof_chen.teamviewer.main_code.config.TeamviewerModMetadata;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.plugin.MinecraftClientObjects;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** NeoForge metadata and filesystem implementation shared by every Minecraft adapter. */
abstract class AbstractNeoForgeRuntimeGateway implements RuntimeGateway {
    protected abstract MinecraftClientObjects clientObjects();

    @Override
    public final String getClientProgramVersion() {
        String modVersion = ModList.get().getModContainerById("team_view_relay")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(TeamviewerModMetadata.MOD_VERSION_FALLBACK);
        return TeamviewerModMetadata.clientProgramVersion(modVersion, getMinecraftVersion());
    }

    @Override
    public final String getLoaderId() {
        return "neoforge";
    }

    @Override
    public final boolean isModLoaded(String modId) {
        return modId != null && ModList.get().isLoaded(modId);
    }

    @Override
    public final String getModVersion(String modId) {
        return modId == null ? "unknown" : ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    }

    @Override
    public final Object getPluginService(String serviceId) {
        return MinecraftClientObjects.SERVICE_ID.equals(serviceId) ? clientObjects() : null;
    }

    @Override
    public final Path getLogsDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("logs");
    }

    @Override
    public final Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
