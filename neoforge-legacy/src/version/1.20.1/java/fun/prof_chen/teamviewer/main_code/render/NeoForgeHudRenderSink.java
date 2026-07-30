package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import fun.prof_chen.teamviewer.main_code.hud.abstraction.HudRenderSink;
import fun.prof_chen.teamviewer.main_code.hud.model.HudFrame;
import fun.prof_chen.teamviewer.main_code.hud.model.HudLine;
import fun.prof_chen.teamviewer.main_code.hud.model.HudPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class NeoForgeHudRenderSink implements HudRenderSink<GuiGraphics> {
    @Override
    public void render(GuiGraphics context, HudFrame frame) {
        Minecraft client = Minecraft.getInstance();
        if (context == null || frame == null || client.font == null) return;
        for (HudPanel panel : frame.panels()) {
            int width = 0;
            for (HudLine line : panel.lines()) width = Math.max(width, client.font.width(toComponent(line.text())));
            int boxWidth = width + panel.paddingX() * 2;
            int boxHeight = panel.paddingY() * 2 + panel.lineHeight() * panel.lines().size();
            int x = panel.anchor() == HudPanel.Anchor.TOP_RIGHT
                    ? context.guiWidth() - panel.xMargin() - boxWidth : panel.xMargin();
            context.fill(x, panel.y(), x + boxWidth, panel.y() + boxHeight, panel.backgroundColor());
            for (int i = 0; i < panel.lines().size(); i++) {
                HudLine line = panel.lines().get(i);
                context.drawString(client.font, toComponent(line.text()), x + panel.paddingX(),
                        panel.y() + panel.paddingY() + i * panel.lineHeight(), line.color(), true);
            }
        }
    }

    private static Component toComponent(UiText value) {
        MutableComponent result = value.translationKey() == null
                ? Component.literal(value.literal() == null ? "" : value.literal())
                : Component.translatable(value.translationKey(),
                        value.arguments().stream().map(NeoForgeHudRenderSink::toComponent).toArray());
        return value.suffix().isEmpty() ? result : result.append(value.suffix());
    }
}
