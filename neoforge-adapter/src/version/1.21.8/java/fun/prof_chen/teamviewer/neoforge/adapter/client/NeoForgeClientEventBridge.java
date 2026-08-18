package fun.prof_chen.teamviewer.neoforge.adapter.client;

import com.mojang.blaze3d.platform.InputConstants;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientEventBridge extends AbstractNeoForgeClientEventBridge<RenderLevelStageEvent> {
    @Override
    protected KeyMapping createKey(String translationKey, int keyCode) {
        return new KeyMapping(translationKey, InputConstants.Type.KEYSYM,
                keyCode, "category.mc_teamviewer.general");
    }

    @Override
    protected void registerWorldEvent(ClientEventHandler<RenderLevelStageEvent, GuiGraphics> handler) {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterEntities event) -> handler.onWorldRender(event));
    }
}
