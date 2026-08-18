package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import fun.prof_chen.teamviewer.main_code.hud.model.HudFrame;
import fun.prof_chen.teamviewer.main_code.hud.model.HudLine;
import fun.prof_chen.teamviewer.main_code.hud.model.HudPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Loader-neutral Minecraft 26.x HUD renderer. */
public final class MinecraftHudRenderer {
    private MinecraftHudRenderer() { }

    public static void render(GuiGraphicsExtractor graphics, HudFrame frame) {
        Minecraft client = Minecraft.getInstance();
        if (graphics == null || frame == null) return;
        for (HudPanel panel : frame.panels()) {
            int width = 0;
            for (HudLine line : panel.lines()) {
                width = Math.max(width, client.font.width(toComponent(line.text())));
            }
            int boxWidth = width + panel.paddingX() * 2;
            int boxHeight = panel.paddingY() * 2 + panel.lineHeight() * panel.lines().size();
            int x = panel.anchor() == HudPanel.Anchor.TOP_RIGHT
                    ? graphics.guiWidth() - panel.xMargin() - boxWidth : panel.xMargin();
            graphics.fill(x, panel.y(), x + boxWidth, panel.y() + boxHeight, panel.backgroundColor());
            for (int index = 0; index < panel.lines().size(); index++) {
                HudLine line = panel.lines().get(index);
                graphics.text(client.font, toComponent(line.text()), x + panel.paddingX(),
                        panel.y() + panel.paddingY() + index * panel.lineHeight(), line.color(), true);
            }
        }
    }

    private static Component toComponent(UiText value) {
        MutableComponent component = value.translationKey() == null
                ? Component.literal(value.literal() == null ? "" : value.literal())
                : Component.translatable(value.translationKey(),
                        value.arguments().stream().map(MinecraftHudRenderer::toComponent).toArray());
        return value.suffix().isEmpty() ? component : component.append(value.suffix());
    }
}
