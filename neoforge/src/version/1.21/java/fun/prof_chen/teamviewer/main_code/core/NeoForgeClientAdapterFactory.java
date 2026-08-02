package fun.prof_chen.teamviewer.main_code.core;

import fun.prof_chen.teamviewer.neoforge.adapter.client.NeoForgeClientEventBridge;
import fun.prof_chen.teamviewer.neoforge.adapter.client.NeoForgeGameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.network.bridge.NeoForgeRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.render.NeoForgeHudRenderSink;
import fun.prof_chen.teamviewer.main_code.render.NeoForgeWorldRenderSink;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Minecraft 1.21 NeoForge SDK adapter factory. */
public final class NeoForgeClientAdapterFactory
        implements ClientAdapterFactory<RenderLevelStageEvent, GuiGraphics> {
    @Override
    public ClientAdapterBundle<RenderLevelStageEvent, GuiGraphics> create() {
        return new ClientAdapterBundle<>(
                "neoforge-1.21",
                new NeoForgeRuntimeGateway(),
                new NeoForgeGameClientBridge(),
                new NeoForgeClientEventBridge(),
                new NeoForgeWorldRenderSink(),
                new NeoForgeHudRenderSink(),
                controller -> {
                    Minecraft client = Minecraft.getInstance();
                    client.setScreen(new ConfigScreen(client.screen, controller));
                },
                new IntegrationRegistry());
    }
}
