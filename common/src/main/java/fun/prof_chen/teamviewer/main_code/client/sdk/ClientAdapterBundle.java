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
        MapAdapterBundle mapAdapters) {
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
    }
}
