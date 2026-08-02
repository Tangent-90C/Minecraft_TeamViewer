package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapClientPlugin implements IClientPlugin {
	private static final String TEAMVIEWER_MOD_ID = "teamviewer";
	private static final Logger LOGGER = LoggerFactory.getLogger(JourneyMapClientPlugin.class);
	private static volatile IClientAPI clientApi;

	@Override
	public void initialize(IClientAPI jmClientApi) {
		clientApi = jmClientApi;
		LOGGER.info("JourneyMap client plugin initialized");
	}

	@Override
	public String getModId() {
		return TEAMVIEWER_MOD_ID;
	}

	/** Dynamic service queried by Lua; null is the normal pre-initialize state. */
	public static Object clientApiService() { return clientApi; }
}
