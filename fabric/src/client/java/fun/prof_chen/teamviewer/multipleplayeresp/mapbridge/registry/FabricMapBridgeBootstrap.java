package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.registry;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.journey.JourneyMapBridgeModule;
import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.xaero.XaeroMapBridgeModule;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricMapBridgeBootstrap {
	private static final String JOURNEYMAP_MOD_ID = "journeymap";
	private static final String XAERO_MINIMAP_MOD_ID = "xaerominimap";
	private static final String XAERO_WORLDMAP_MOD_ID = "xaeroworldmap";

	private FabricMapBridgeBootstrap() {
	}

	public static MapBridgeRegistry createRegistry() {
		MapBridgeRegistry registry = new MapBridgeRegistry();
		if (isXaeroInstalled()) {
			new XaeroMapBridgeModule().register(registry);
		}
		if (isJourneyMapInstalled()) {
			new JourneyMapBridgeModule().register(registry);
		}
		return registry;
	}

	private static boolean isXaeroInstalled() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isModLoaded(XAERO_MINIMAP_MOD_ID) || loader.isModLoaded(XAERO_WORLDMAP_MOD_ID);
	}

	private static boolean isJourneyMapInstalled() {
		return FabricLoader.getInstance().isModLoaded(JOURNEYMAP_MOD_ID);
	}
}
