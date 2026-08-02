package fun.prof_chen.teamviewer.main_code.core;

import fun.prof_chen.teamviewer.main_code.client.bridge.FabricClientEventBridge;
import fun.prof_chen.teamviewer.main_code.client.bridge.FabricGameClientBridge;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterBundle;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientAdapterFactory;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.network.bridge.FabricRuntimeGateway;
import fun.prof_chen.teamviewer.main_code.render.FabricHudRenderSink;
import fun.prof_chen.teamviewer.main_code.render.FabricWorldRenderSink;
import fun.prof_chen.teamviewer.main_code.screen.ConfigScreen;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Minecraft 1.21.11 source adapter factory. */
public final class FabricClientAdapterFactory implements ClientAdapterFactory<WorldExtractionContext, DrawContext> {
    @Override
    public ClientAdapterBundle<WorldExtractionContext, DrawContext> create() {
        return new ClientAdapterBundle<>(
                "1.21.11",
                new FabricRuntimeGateway(),
                new FabricGameClientBridge(),
                new FabricClientEventBridge(),
                new FabricWorldRenderSink(),
                new FabricHudRenderSink(),
                controller -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.setScreen(new ConfigScreen(client.currentScreen, controller));
                },
                new IntegrationRegistry());
    }
}
