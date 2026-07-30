package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import fun.prof_chen.teamviewer.main_code.hud.abstraction.HudRenderSink;
import fun.prof_chen.teamviewer.main_code.hud.model.HudFrame;
import fun.prof_chen.teamviewer.main_code.hud.model.HudLine;
import fun.prof_chen.teamviewer.main_code.hud.model.HudPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public final class FabricHudRenderSink implements HudRenderSink<MatrixStack> {
    @Override
    public void render(MatrixStack context, HudFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || frame == null || client.textRenderer == null) return;
        for (HudPanel panel : frame.panels()) {
            int width = 0;
            for (HudLine line : panel.lines()) width = Math.max(width, client.textRenderer.getWidth(toText(line.text())));
            int boxWidth = width + panel.paddingX() * 2;
            int boxHeight = panel.paddingY() * 2 + panel.lineHeight() * panel.lines().size();
            int x = panel.anchor() == HudPanel.Anchor.TOP_RIGHT
                    ? client.getWindow().getScaledWidth() - panel.xMargin() - boxWidth : panel.xMargin();
            DrawableHelper.fill(context, x, panel.y(), x + boxWidth, panel.y() + boxHeight, panel.backgroundColor());
            for (int i = 0; i < panel.lines().size(); i++) {
                HudLine line = panel.lines().get(i);
                DrawableHelper.drawTextWithShadow(context, client.textRenderer, toText(line.text()), x + panel.paddingX(),
                        panel.y() + panel.paddingY() + i * panel.lineHeight(), line.color());
            }
        }
    }

    private static Text toText(UiText value) {
        MutableText text = value.translationKey() == null
                ? new LiteralText(value.literal() == null ? "" : value.literal())
                : new TranslatableText(value.translationKey(), value.arguments().stream().map(FabricHudRenderSink::toText).toArray());
        return value.suffix().isEmpty() ? text : text.append(value.suffix());
    }
}
