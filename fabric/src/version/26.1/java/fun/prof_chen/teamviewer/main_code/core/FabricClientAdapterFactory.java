package fun.prof_chen.teamviewer.main_code.core;

import fun.prof_chen.teamviewer.main_code.client.bridge.FabricClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricGameClientBridge;
import fun.prof_chen.teamviewer.main_code.bridge.MinecraftClientUiCompat;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.network.bridge.FabricRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.render.FabricHudRenderSink;
import fun.prof_chen.teamviewer.main_code.render.FabricWorldRenderSink;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Minecraft 26.1-26.2 native adapter factory. */
public final class FabricClientAdapterFactory implements ClientAdapterFactory<LevelRenderContext, GuiGraphicsExtractor> {
    @Override
    public ClientAdapterBundle<LevelRenderContext, GuiGraphicsExtractor> create() {
        return new ClientAdapterBundle<>(
                "26.1-26.2",
                new FabricRuntimeGateway(),
                new FabricGameClientBridge(),
                new FabricClientEventBridge(),
                new FabricWorldRenderSink(),
                new FabricHudRenderSink(),
                controller -> {
                    Minecraft client = Minecraft.getInstance();
                    MinecraftClientUiCompat.setScreen(client,
                            new ConfigScreen(MinecraftClientUiCompat.currentScreen(client), controller));
                },
                new IntegrationRegistry());
    }
}
