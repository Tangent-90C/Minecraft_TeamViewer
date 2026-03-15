package fun.prof_chen.teamviewer.main_code.mapbridge.abstraction;

public interface PluginEntrypointMapBridgeModule extends MapBridgeModule {
	@Override
	default MapBridgeIntegrationMode integrationMode() {
		return MapBridgeIntegrationMode.PLUGIN_ENTRYPOINT;
	}
}