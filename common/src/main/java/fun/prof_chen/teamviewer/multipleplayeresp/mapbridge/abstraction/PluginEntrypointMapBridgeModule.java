package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.abstraction;

public interface PluginEntrypointMapBridgeModule extends MapBridgeModule {
	@Override
	default MapBridgeIntegrationMode integrationMode() {
		return MapBridgeIntegrationMode.PLUGIN_ENTRYPOINT;
	}
}