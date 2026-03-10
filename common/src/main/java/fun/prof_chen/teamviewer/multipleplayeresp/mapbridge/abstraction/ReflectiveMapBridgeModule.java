package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction;

public interface ReflectiveMapBridgeModule extends MapBridgeModule {
	@Override
	default MapBridgeIntegrationMode integrationMode() {
		return MapBridgeIntegrationMode.REFLECTIVE;
	}
}