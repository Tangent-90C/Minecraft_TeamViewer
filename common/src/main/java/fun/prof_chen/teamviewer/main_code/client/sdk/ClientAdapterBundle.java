package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapNativeBridge;
import fun.prof_chen.teamviewer.main_code.client.bridge.GameClientBridge;
import fun.prof_chen.teamviewer.main_code.hud.abstraction.HudRenderSink;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;

import java.util.Objects;

/** The complete compile-time contract every Minecraft version adapter must provide. */
public record ClientAdapterBundle(
        ClientAdapterDescriptor descriptor,
        RuntimeGateway runtimeGateway,
        GameClientBridge gameClientBridge,
        ClientEventBridge eventBridge,
        WorldRenderSink<Object> worldRenderSink,
        HudRenderSink<Object> hudRenderSink,
        ConfigScreenHost configScreenHost,
        BattleMapNativeBridge battleMapNativeBridge,
        MapAdapterBundle mapAdapters) {
    public ClientAdapterBundle {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(runtimeGateway, "runtimeGateway");
        Objects.requireNonNull(gameClientBridge, "gameClientBridge");
        Objects.requireNonNull(eventBridge, "eventBridge");
        Objects.requireNonNull(worldRenderSink, "worldRenderSink");
        Objects.requireNonNull(hudRenderSink, "hudRenderSink");
        Objects.requireNonNull(configScreenHost, "configScreenHost");
        Objects.requireNonNull(battleMapNativeBridge, "battleMapNativeBridge");
        Objects.requireNonNull(mapAdapters, "mapAdapters");
    }
}
