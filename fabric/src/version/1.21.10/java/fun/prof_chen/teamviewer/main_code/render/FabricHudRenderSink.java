package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import fun.prof_chen.teamviewer.main_code.hud.abstraction.HudRenderSink;
import fun.prof_chen.teamviewer.main_code.hud.model.HudFrame;
import fun.prof_chen.teamviewer.main_code.hud.model.HudLine;
import fun.prof_chen.teamviewer.main_code.hud.model.HudPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class FabricHudRenderSink implements HudRenderSink<DrawContext> {
    @Override
    public void render(DrawContext context, HudFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || frame == null || client.textRenderer == null) return;
        for (HudPanel panel : frame.panels()) {
            int width = 0;
            for (HudLine line : panel.lines()) width = Math.max(width, client.textRenderer.getWidth(toText(line.text())));
            int boxWidth = width + panel.paddingX() * 2;
            int boxHeight = panel.paddingY() * 2 + panel.lineHeight() * panel.lines().size();
            int x = panel.anchor() == HudPanel.Anchor.TOP_RIGHT
                    ? context.getScaledWindowWidth() - panel.xMargin() - boxWidth : panel.xMargin();
            context.fill(x, panel.y(), x + boxWidth, panel.y() + boxHeight, panel.backgroundColor());
            for (int i = 0; i < panel.lines().size(); i++) {
                HudLine line = panel.lines().get(i);
                context.drawTextWithShadow(client.textRenderer, toText(line.text()), x + panel.paddingX(),
                        panel.y() + panel.paddingY() + i * panel.lineHeight(), line.color());
            }
        }
    }

    private static Text toText(UiText value) {
        MutableText text = value.translationKey() == null
                ? Text.literal(value.literal() == null ? "" : value.literal())
                : Text.translatable(value.translationKey(), value.arguments().stream().map(FabricHudRenderSink::toText).toArray());
        return value.suffix().isEmpty() ? text : text.append(value.suffix());
    }
}
