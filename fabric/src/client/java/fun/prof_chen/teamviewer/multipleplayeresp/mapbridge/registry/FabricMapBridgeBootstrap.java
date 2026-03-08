package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.registry;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.xaero.XaeroMapBridgeModule;

public final class FabricMapBridgeBootstrap {
	private FabricMapBridgeBootstrap() {
	}

	public static MapBridgeRegistry createRegistry() {
		MapBridgeRegistry registry = new MapBridgeRegistry();
		new XaeroMapBridgeModule().register(registry);
		return registry;
	}
}
