package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

final class FabricMatrixConfigScreenCompat {
    private FabricMatrixConfigScreenCompat() { }

    static ClickableWidget button(UiRect bounds, Text label, Runnable action) {
        return ButtonWidget.builder(label, ignored -> action.run())
                .dimensions(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build();
    }

    static MutableText emptyText() { return Text.empty(); }
    static MutableText literalText(String value) { return Text.literal(value); }
    static MutableText translatableText(String key, Object... arguments) {
        return Text.translatable(key, arguments);
    }
}
