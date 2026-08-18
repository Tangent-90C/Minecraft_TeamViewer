package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlView;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

final class FabricConfigScreenCompat {
    private FabricConfigScreenCompat() { }

    static ClickableWidget checkbox(TextRenderer renderer, UiRect bounds, Text text, boolean checked,
                                    Text tooltip, Consumer<Boolean> changed) {
        CheckboxWidget checkbox = new CheckboxWidget(
                bounds.x(), bounds.y(), bounds.width(), bounds.height(), text, checked) {
            @Override public void onPress() { super.onPress(); changed.accept(isChecked()); }
        };
        if (tooltip != null) checkbox.setTooltip(Tooltip.of(tooltip));
        return checkbox;
    }

    static ClickableWidget label(TextRenderer renderer, UiRect bounds, Text text, int color,
                                 ConfigControlView.TextAlignment alignment) {
        TextWidget label = new TextWidget(bounds.x(), bounds.y(), bounds.width(), bounds.height(), text, renderer);
        if (alignment == ConfigControlView.TextAlignment.CENTER) label.alignCenter(); else label.alignLeft();
        return label.setTextColor(color);
    }

    static void refreshLabel(ClickableWidget widget, Text text, int color) {
        if (widget instanceof TextWidget label) label.setTextColor(color);
    }
}
