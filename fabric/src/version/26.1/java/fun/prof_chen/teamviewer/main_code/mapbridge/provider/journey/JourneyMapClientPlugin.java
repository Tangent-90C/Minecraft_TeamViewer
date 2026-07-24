package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JourneyMap 6 entrypoint; bridge registration is intentionally API-only on 26.1. */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapClientPlugin implements IClientPlugin {
    static final String TEAMVIEWER_MOD_ID = "teamviewer";
    private static final Logger LOGGER = LoggerFactory.getLogger(JourneyMapClientPlugin.class);
    private static volatile IClientAPI clientApi;

    @Override
    public String getModId() {
        return TEAMVIEWER_MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        clientApi = api;
        LOGGER.info("JourneyMap 26.1 client plugin initialized");
    }

    static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("journeymap") && clientApi != null;
    }

    static IntegrationSupportStatus supportStatus() {
        return FabricLoader.getInstance().isModLoaded("journeymap")
                ? IntegrationSupportStatus.AVAILABLE : IntegrationSupportStatus.MOD_NOT_INSTALLED;
    }

    static IClientAPI clientApi() {
        return clientApi;
    }
}
