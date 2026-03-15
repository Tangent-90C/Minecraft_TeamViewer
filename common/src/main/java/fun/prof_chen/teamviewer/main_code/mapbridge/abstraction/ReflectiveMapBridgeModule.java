package fun.prof_chen.teamviewer.main_code.mapbridge.abstraction;

public interface ReflectiveMapBridgeModule extends MapBridgeModule {
	@Override
	default MapBridgeIntegrationMode integrationMode() {
		return MapBridgeIntegrationMode.REFLECTIVE;
	}
}