package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.registry.MapBridgeRegistry;

import java.util.List;

public interface MapBridgeModule {
	String providerId();

	List<String> activationModIds();

	MapBridgeIntegrationMode integrationMode();

	default String displayName() {
		return providerId();
	}

	void register(MapBridgeRegistry registry);
}
