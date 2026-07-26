package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapNativeBridge;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.hud.abstraction.HudRenderSink;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;

import java.util.Objects;

/** The complete compile-time contract every Minecraft version adapter must provide. */
public record ClientAdapterBundle<W, H>(
        String adapterVersion,
        RuntimeGateway runtimeGateway,
        GameClientBridge gameClientBridge,
        ClientEventBridge<W, H> eventBridge,
        WorldRenderSink<W> worldRenderSink,
        HudRenderSink<H> hudRenderSink,
        ConfigScreenHost configScreenHost,
        BattleMapNativeBridge battleMapNativeBridge,
        MapAdapterBundle mapAdapters,
        IntegrationRegistry integrationRegistry) {
    public ClientAdapterBundle(
            String adapterVersion,
            RuntimeGateway runtimeGateway,
            GameClientBridge gameClientBridge,
            ClientEventBridge<W, H> eventBridge,
            WorldRenderSink<W> worldRenderSink,
            HudRenderSink<H> hudRenderSink,
            ConfigScreenHost configScreenHost,
            BattleMapNativeBridge battleMapNativeBridge,
            MapAdapterBundle mapAdapters) {
        this(adapterVersion, runtimeGateway, gameClientBridge, eventBridge, worldRenderSink, hudRenderSink,
                configScreenHost, battleMapNativeBridge, mapAdapters,
                createRegistry(battleMapNativeBridge, mapAdapters));
    }

    public ClientAdapterBundle {
        adapterVersion = Objects.requireNonNull(adapterVersion, "adapterVersion").trim();
        if (adapterVersion.isEmpty()) throw new IllegalArgumentException("adapterVersion must not be blank");
        Objects.requireNonNull(runtimeGateway, "runtimeGateway");
        Objects.requireNonNull(gameClientBridge, "gameClientBridge");
        Objects.requireNonNull(eventBridge, "eventBridge");
        Objects.requireNonNull(worldRenderSink, "worldRenderSink");
        Objects.requireNonNull(hudRenderSink, "hudRenderSink");
        Objects.requireNonNull(configScreenHost, "configScreenHost");
        Objects.requireNonNull(battleMapNativeBridge, "battleMapNativeBridge");
        Objects.requireNonNull(mapAdapters, "mapAdapters");
        Objects.requireNonNull(integrationRegistry, "integrationRegistry");
    }

    private static IntegrationRegistry createRegistry(
            BattleMapNativeBridge battleMapNativeBridge, MapAdapterBundle mapAdapters) {
        IntegrationRegistry registry = new IntegrationRegistry();
        for (var projection : mapAdapters.remotePlayerProjections()) {
            registry.registerNative(new IntegrationCapability(
                    projection.id(), IntegrationRole.REMOTE_PLAYER.id(), projection.supportStatus(),
                    projection.supportDetail(), IntegrationIds.pluginIdForCapability(projection.id()),
                    projection.supportStatus() == IntegrationSupportStatus.AVAILABLE
                            ? IntegrationImplementationSource.JAVA_NATIVE
                            : IntegrationImplementationSource.PLACEHOLDER,
                    PluginRuntimeStatus.DISABLED), projection);
        }
        for (var adapter : mapAdapters.sharedWaypointAdapters()) {
            registry.registerNative(new IntegrationCapability(
                    adapter.id(), IntegrationRole.SHARED_WAYPOINT.id(), adapter.supportStatus(),
                    adapter.supportDetail(), IntegrationIds.pluginIdForCapability(adapter.id()),
                    adapter.supportStatus() == IntegrationSupportStatus.AVAILABLE
                            ? IntegrationImplementationSource.JAVA_NATIVE
                            : IntegrationImplementationSource.PLACEHOLDER,
                    PluginRuntimeStatus.DISABLED), adapter);
        }
        registry.declare(IntegrationIds.SIMMC_BATTLE_MAP, IntegrationRole.BATTLE_MAP_SOURCE.id(),
                IntegrationIds.PLUGIN_SIMMC, "SimMC Native Battle Map",
                IntegrationSupportStatus.ENTRYPOINT_NOT_READY,
                "Lua battle-map adapter has not loaded yet");
        registry.declare(IntegrationIds.NODEMC_BATTLE_MAP, IntegrationRole.BATTLE_MAP_SOURCE.id(),
                IntegrationIds.PLUGIN_NODEMC, "NodeMC Scoreboard Battle Map",
                IntegrationSupportStatus.ENTRYPOINT_NOT_READY,
                "Lua scoreboard adapter has not loaded yet");
        return registry;
    }
}
