package fun.prof_chen.teamviewer.main_code.mapbridge.registry;

import fun.prof_chen.teamviewer.main_code.mapbridge.abstraction.MapBridgeModule;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey.JourneyMapBridgeModule;
import fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero.XaeroMapBridgeModule;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class FabricMapBridgeBootstrap {
	private static final Logger LOGGER = LoggerFactory.getLogger(FabricMapBridgeBootstrap.class);
	private static final List<MapBridgeModule> MODULES = List.of(
			new XaeroMapBridgeModule(),
			new JourneyMapBridgeModule());

	private FabricMapBridgeBootstrap() {
	}

	public static MapBridgeRegistry createRegistry() {
		MapBridgeRegistry registry = new MapBridgeRegistry();
		for (MapBridgeModule module : MODULES) {
			if (!isActive(module)) {
				LOGGER.debug("Skipping {} map bridge module: integrationMode={}, activationMods={}",
						module.displayName(),
						module.integrationMode(),
						module.activationModIds());
				continue;
			}
			module.register(registry);
			LOGGER.info("Registered {} map bridge module: integrationMode={}, activationMods={}",
					module.displayName(),
					module.integrationMode(),
					module.activationModIds());
		}
		return registry;
	}

	private static boolean isActive(MapBridgeModule module) {
		FabricLoader loader = FabricLoader.getInstance();
		return module.activationModIds().stream().anyMatch(loader::isModLoaded);
	}
}
