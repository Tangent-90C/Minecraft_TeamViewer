package fun.prof_chen.teamviewer.main_code.core;

import fun.prof_chen.teamviewer.neoforge.adapter.battlemap.NeoForgeUnsupportedBattleMapBridge;
import fun.prof_chen.teamviewer.neoforge.adapter.client.NeoForgeClientEventBridge;
import fun.prof_chen.teamviewer.neoforge.adapter.client.NeoForgeGameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory;
import fun.prof_chen.teamviewer.main_code.network.bridge.NeoForgeRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.render.NeoForgeHudRenderSink;
import fun.prof_chen.teamviewer.main_code.render.NeoForgeWorldRenderSink;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Minecraft 26.1.2 NeoForge SDK adapter factory. */
public final class NeoForgeClientAdapterFactory
        implements ClientAdapterFactory<RenderLevelStageEvent, GuiGraphicsExtractor> {
    @Override
    public ClientAdapterBundle<RenderLevelStageEvent, GuiGraphicsExtractor> create() {
        return new ClientAdapterBundle<>(
                "neoforge-26.1",
                new NeoForgeRuntimeGateway(),
                new NeoForgeGameClientBridge(),
                new NeoForgeClientEventBridge(),
                new NeoForgeWorldRenderSink(),
                new NeoForgeHudRenderSink(),
                controller -> {
                    Minecraft client = Minecraft.getInstance();
                    client.setScreen(new ConfigScreen(client.screen, controller));
                },
                new NeoForgeUnsupportedBattleMapBridge(),
                NeoForgeMapAdapters.unsupported());
    }
}
