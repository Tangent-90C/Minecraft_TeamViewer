package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlView;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class NeoForgeConfigScreenCompat {
    private NeoForgeConfigScreenCompat() { }

    static AbstractWidget checkbox(Font font, UiRect bounds, Component label, boolean checked,
                                   Component tooltip, Consumer<Boolean> changed) {
        Checkbox.Builder builder = Checkbox.builder(label, font).pos(bounds.x(), bounds.y())
                .selected(checked).onValueChange((ignored, value) -> changed.accept(value));
        if (tooltip != null) builder.tooltip(Tooltip.create(tooltip));
        return builder.build();
    }

    static AbstractWidget label(Font font, UiRect bounds, Component text, int color,
                                ConfigControlView.TextAlignment alignment) {
        StringWidget label = new StringWidget(bounds.x(), bounds.y(), bounds.width(), bounds.height(), text, font);
        if (alignment == ConfigControlView.TextAlignment.CENTER) label.alignCenter();
        else label.alignLeft();
        return label.setColor(color);
    }

    static void refreshLabel(AbstractWidget widget, int color) {
        if (widget instanceof StringWidget label) label.setColor(color);
    }

    static void showTooltip(GuiGraphics graphics, Font font, Component text, int x, int y) {
        graphics.renderTooltip(font, text, x, y);
    }
}
