package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.hud.abstraction.HudRenderSink;
import fun.prof_chen.teamviewer.main_code.hud.model.HudFrame;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FabricHudRenderSink implements HudRenderSink<GuiGraphicsExtractor> {
    @Override
    public void render(GuiGraphicsExtractor graphics, HudFrame frame) {
        MinecraftHudRenderer.render(graphics, frame);
    }
}
