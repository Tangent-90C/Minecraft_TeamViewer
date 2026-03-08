package fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.minimap;

import fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction.MinimapBridgeRegistry;

public final class FabricMinimapBridgeBootstrap {
	private FabricMinimapBridgeBootstrap() {
	}

	public static MinimapBridgeRegistry createRegistry() {
		MinimapBridgeRegistry registry = new MinimapBridgeRegistry();
		new XaeroMinimapBridgeModule().register(registry);
		return registry;
	}
}
