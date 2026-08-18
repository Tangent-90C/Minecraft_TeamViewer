package fun.prof_chen.teamviewer.main_code.network.bridge;

import fun.prof_chen.teamviewer.main_code.config.FabricModVersionProvider;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.plugin.MinecraftClientObjects;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Fabric Loader metadata and filesystem implementation shared by every Minecraft adapter. */
abstract class AbstractFabricRuntimeGateway implements RuntimeGateway {
    protected abstract MinecraftClientObjects clientObjects();

    @Override
    public final String getClientProgramVersion() {
        return FabricModVersionProvider.getModVersion();
    }

    @Override
    public final String getMinecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public final String getLoaderId() {
        return "fabric";
    }

    @Override
    public final boolean isModLoaded(String modId) {
        return modId != null && FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public final String getModVersion(String modId) {
        return modId == null ? "unknown" : FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    @Override
    public final Object getPluginService(String serviceId) {
        if (MinecraftClientObjects.SERVICE_ID.equals(serviceId)) return clientObjects();
        return "journeymap.client_api".equals(serviceId)
                ? fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapClientPlugin
                        .clientApiService()
                : null;
    }

    @Override
    public final Path getLogsDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("logs");
    }

    @Override
    public final Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
