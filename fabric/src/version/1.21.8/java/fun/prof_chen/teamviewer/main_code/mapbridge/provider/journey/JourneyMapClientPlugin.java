package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JourneyMapPlugin(apiVersion = "2.0.0")
@journeymap.api.v2.common.JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapClientPlugin implements IClientPlugin {
	static final String JOURNEYMAP_MOD_ID = "journeymap";
	static final String TEAMVIEWER_MOD_ID = "teamviewer";
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

	static boolean isModLoaded() {
		return FabricLoader.getInstance().isModLoaded(JOURNEYMAP_MOD_ID);
	}

	static boolean isAvailable() {
		return isModLoaded() && clientApi != null;
	}

	static IntegrationSupportStatus supportStatus() {
		return isModLoaded() ? IntegrationSupportStatus.AVAILABLE : IntegrationSupportStatus.MOD_NOT_INSTALLED;
	}

	static IClientAPI clientApi() {
		return clientApi;
	}
}
