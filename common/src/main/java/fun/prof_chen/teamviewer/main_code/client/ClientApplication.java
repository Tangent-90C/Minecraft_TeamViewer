package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.bridge.WaypointSyncGateway;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import fun.prof_chen.teamviewer.main_code.client.sdk.AdapterRuntimeTck;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiSession;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiSessions;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.network.transport.OkHttpTransportProcess;
import fun.prof_chen.teamviewer.main_code.plugin.IntegrationPluginManager;
import fun.prof_chen.teamviewer.main_code.plugin.PluginHostAccess;
import fun.prof_chen.teamviewer.main_code.sync.api.RemotePlayerRepository;
import fun.prof_chen.teamviewer.main_code.sync.api.SharedWaypointRepository;
import fun.prof_chen.teamviewer.main_code.sync.core.RemotePlayerProjectionCoordinator;
import fun.prof_chen.teamviewer.main_code.sync.core.SharedWaypointSyncCoordinator;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedRemotePlayerRepository;
import fun.prof_chen.teamviewer.main_code.sync.impl.repository.MapBackedSharedWaypointRepository;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single common client composition root. A Minecraft version supplies adapters only; common
 * owns configuration, network lifecycle, repositories, synchronization and feature policy.
 */
public final class ClientApplication<W, H> implements ClientEventHandler<W, H> {
    public static final String CONFIG_FILE_NAME = "team-view-relay.json";

    private final ClientAdapterBundle<W, H> adapters;
    private final ClientCoordinator coordinator;
    private final SharedWaypointSyncCoordinator waypointCoordinator;
    private final IntegrationPluginManager pluginManager;
    private final AtomicBoolean stopped = new AtomicBoolean();

    private ClientApplication(ClientAdapterBundle<W, H> adapters) {
        this.adapters = Objects.requireNonNull(adapters, "adapters");
        Config config = Config.load(adapters.runtimeGateway().getConfigDirectory().resolve(CONFIG_FILE_NAME));
        IntegrationRegistry integrations = adapters.integrationRegistry();
        Map<UUID, RemotePlayerInfo> remotePlayers = new ConcurrentHashMap<>();
        Map<String, SharedWaypointInfo> sharedWaypoints = new ConcurrentHashMap<>();
        pluginManager = new IntegrationPluginManager(adapters.runtimeGateway(), integrations, config,
                new PluginHostAccess(
                        adapters.gameClientBridge()::captureWorldSnapshot,
                        () -> adapters.gameClientBridge().captureReportSnapshot(false).players(),
                        () -> Map.copyOf(sharedWaypoints),
                        adapters.gameClientBridge()::captureScoreboardSnapshot));
        NetworkManager network = new NetworkManager(
                remotePlayers, adapters.runtimeGateway(), new OkHttpTransportProcess());
        NetworkManager.setConfigGateway(config);
        coordinator = new ClientCoordinator(config, network, adapters.gameClientBridge());
        ClientServices.install(coordinator);

        RemotePlayerRepository remoteRepository = new MapBackedRemotePlayerRepository(remotePlayers);
        SharedWaypointRepository waypointRepository = new MapBackedSharedWaypointRepository(sharedWaypoints);
        WaypointSyncGateway waypointGateway = new WaypointSyncGateway(network);
        RemotePlayerProjectionCoordinator projectionCoordinator =
                new RemotePlayerProjectionCoordinator(integrations);
        waypointCoordinator = new SharedWaypointSyncCoordinator(
                waypointRepository, waypointGateway, integrations,
                config, adapters.gameClientBridge());
        waypointCoordinator.start();
        coordinator.configureRuntimeSupport(
                remoteRepository, waypointRepository, waypointCoordinator, waypointGateway, projectionCoordinator);
        coordinator.configureBattleMapSupport(integrations);
        coordinator.configurePluginManager(pluginManager);
        ConfigUiSessions.install(() -> new ConfigUiSession(coordinator));
    }

    public static <W, H> ClientApplication<W, H> start(ClientAdapterBundle<W, H> adapters) {
        ClientApplication<W, H> application = new ClientApplication<>(adapters);
        adapters.eventBridge().register(application);
        return application;
    }

    public ClientCoordinator coordinator() {
        return coordinator;
    }

    @Override
    public void onEndClientTick() {
        if (!stopped.get()) coordinator.onEndClientTick();
    }

    @Override
    public void onToggleRequested() {
        if (!stopped.get()) coordinator.setEnabled(!coordinator.isEnabled());
    }

    @Override
    public void onConfigRequested() {
        if (!stopped.get()) adapters.configScreenHost().open(new ConfigUiSession(coordinator));
    }

    @Override
    public void onQuickMarkRequested() {
        if (!stopped.get()) coordinator.handleQuickMarkAction(true);
    }

    @Override
    public void onJoinedMultiplayer() {
        if (!stopped.get()) coordinator.onJoinedMultiplayer();
    }

    @Override
    public void onLeftPlaySession() {
        if (!stopped.get()) coordinator.onLeftPlaySession();
    }

    @Override
    public void onClientStopping() {
        if (!stopped.compareAndSet(false, true)) return;
        coordinator.onLeftPlaySession();
        pluginManager.shutdown();
        waypointCoordinator.stop();
        ClientServices.clear(coordinator);
        ConfigUiSessions.clear();
        AdapterRuntimeTck.clear();
    }

    @Override
    public void onWorldRender(W context) {
        if (!stopped.get()) {
            adapters.worldRenderSink().render(context, coordinator.buildWorldRenderFrame());
            AdapterRuntimeTck.markWorldRenderSucceeded();
        }
    }

    @Override
    public void onHudRender(H context) {
        if (!stopped.get()) {
            adapters.hudRenderSink().render(context, coordinator.buildHudFrame());
            AdapterRuntimeTck.markHudRenderSucceeded();
        }
    }
}
