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
        CheckboxWidget.Builder builder = CheckboxWidget.builder(text, renderer).pos(bounds.x(), bounds.y())
                .maxWidth(bounds.width()).checked(checked)
                .callback((ignored, value) -> changed.accept(value));
        if (tooltip != null) builder.tooltip(Tooltip.of(tooltip));
        return builder.build();
    }

    static ClickableWidget label(TextRenderer renderer, UiRect bounds, Text text, int color,
                                 ConfigControlView.TextAlignment alignment) {
        Text styled = text.copy().styled(style -> style.withColor(color & 0xFFFFFF));
        int width = Math.max(1, Math.min(bounds.width(), renderer.getWidth(styled)));
        int x = alignment == ConfigControlView.TextAlignment.CENTER
                ? bounds.x() + (bounds.width() - width) / 2 : bounds.x();
        return new TextWidget(x, bounds.y(), width, bounds.height(), styled, renderer);
    }

    static void refreshLabel(ClickableWidget widget, Text text, int color) {
        widget.setMessage(text.copy().styled(style -> style.withColor(color & 0xFFFFFF)));
    }
}
