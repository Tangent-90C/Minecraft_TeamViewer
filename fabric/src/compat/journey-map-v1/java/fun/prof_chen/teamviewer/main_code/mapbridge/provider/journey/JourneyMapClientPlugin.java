package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JourneyMap 5.x / API 1.x entrypoint. */
@ClientPlugin
public final class JourneyMapClientPlugin implements IClientPlugin {
    private static final String TEAMVIEWER_MOD_ID = "teamviewer";
    private static final Logger LOGGER = LoggerFactory.getLogger(JourneyMapClientPlugin.class);
    private static volatile IClientAPI clientApi;

    @Override
    public void initialize(IClientAPI api) {
        clientApi = api;
        LOGGER.info("JourneyMap API 1.x client plugin initialized");
    }

    @Override
    public String getModId() {
        return TEAMVIEWER_MOD_ID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        // TeamViewRelay only needs the dynamic API service.
    }

    public static Object clientApiService() {
        return clientApi;
    }
}
