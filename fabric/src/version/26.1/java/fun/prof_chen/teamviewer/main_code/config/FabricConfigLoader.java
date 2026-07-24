package fun.prof_chen.teamviewer.main_code.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class FabricConfigLoader {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("team-view-relay.json");

    private FabricConfigLoader() {
    }

    public static Config load() {
        return Config.load(CONFIG_PATH);
    }
}
