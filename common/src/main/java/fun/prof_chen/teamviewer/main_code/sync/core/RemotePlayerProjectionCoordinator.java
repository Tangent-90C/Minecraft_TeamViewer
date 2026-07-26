package fun.prof_chen.teamviewer.main_code.sync.core;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRole;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RemotePlayerProjectionCoordinator {
	private final IntegrationRegistry integrations;
	private final Config config;
	private final GameClientBridge game;

	public RemotePlayerProjectionCoordinator(IntegrationRegistry integrations, Config config, GameClientBridge game) {
		this.integrations = integrations;
		this.config = config;
		this.game = game;
	}

	public RemotePlayerProjectionCoordinator(List<RemotePlayerProjection> projections, Config config, GameClientBridge game) {
		this(registry(projections), config, game);
	}

	private static IntegrationRegistry registry(List<RemotePlayerProjection> projections) {
		IntegrationRegistry registry = new IntegrationRegistry();
		for (RemotePlayerProjection projection : projections) {
			String pluginId = "test." + projection.id();
			registry.registerNative(new IntegrationCapability(projection.id(), IntegrationRole.REMOTE_PLAYER.id(),
					projection.supportStatus(), projection.supportDetail(), pluginId,
					IntegrationImplementationSource.JAVA_NATIVE, PluginRuntimeStatus.ACTIVE), projection);
			registry.setPluginRuntime(pluginId, PluginRuntimeStatus.ACTIVE, "");
		}
		return registry;
	}

	public void tick(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		Map<UUID, RemotePlayerInfo> filtered = filter(players);
		for (RemotePlayerProjection projection : integrations.activeRemotePlayerProjections()) {
			if (!projection.isAvailable()) {
				continue;
			}
			projection.sync(filtered, enabled);
		}
	}

	private Map<UUID, RemotePlayerInfo> filter(Map<UUID, RemotePlayerInfo> players) {
		ClientWorldSnapshot world = game.captureWorldSnapshot();
		if (!world.available() || players == null || players.isEmpty()) return Map.of();
		Map<UUID, RemotePlayerInfo> result = new LinkedHashMap<>();
		for (Map.Entry<UUID, RemotePlayerInfo> entry : players.entrySet()) {
			RemotePlayerInfo value = entry.getValue();
			if (value == null || value.uuid() == null || value.position() == null
					|| value.uuid().equals(world.localPlayerId())
					|| (value.dimension() != null && !value.dimension().isBlank()
					&& !value.dimension().equals(world.dimension()))) continue;
			result.put(entry.getKey(), value);
		}
		return result;
	}

	public void clear() {
		for (RemotePlayerProjection projection : integrations.activeRemotePlayerProjections()) {
			projection.clear();
		}
	}

}
