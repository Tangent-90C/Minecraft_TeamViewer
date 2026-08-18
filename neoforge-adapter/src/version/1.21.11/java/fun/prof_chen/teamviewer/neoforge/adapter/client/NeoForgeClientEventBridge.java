package fun.prof_chen.teamviewer.neoforge.adapter.client;

import com.mojang.blaze3d.platform.InputConstants;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientEventBridge
        extends AbstractNeoForgeClientEventBridge<ExtractLevelRenderStateEvent> {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("mc_teamviewer", "general"));

    @Override
    protected KeyMapping createKey(String translationKey, int keyCode) {
        return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    @Override
    protected void registerWorldEvent(ClientEventHandler<ExtractLevelRenderStateEvent, GuiGraphics> handler) {
        NeoForge.EVENT_BUS.addListener((ExtractLevelRenderStateEvent event) -> handler.onWorldRender(event));
    }
}
