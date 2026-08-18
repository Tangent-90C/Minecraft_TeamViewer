package fun.prof_chen.teamviewer.main_code.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Tooltip API used by NeoForge 1.21.8. */
final class PluginManagerTooltipCompat {
    private PluginManagerTooltipCompat() { }

    static void show(GuiGraphics graphics, Font font, Component text, int mouseX, int mouseY) {
        graphics.setTooltipForNextFrame(text, mouseX, mouseY);
    }
}
