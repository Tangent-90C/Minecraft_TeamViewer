package fun.prof_chen.teamviewer.main_code.config;

import net.fabricmc.loader.api.FabricLoader;

public final class FabricModVersionProvider {
    private FabricModVersionProvider() {
    }

    public static String getModVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer(TeamviewerModMetadata.MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse(TeamviewerModMetadata.MOD_VERSION_FALLBACK);
        } catch (Exception ignored) {
            return TeamviewerModMetadata.MOD_VERSION_FALLBACK;
        }
    }
}
