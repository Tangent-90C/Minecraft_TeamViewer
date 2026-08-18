package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

final class FabricMatrixConfigScreenCompat {
    private FabricMatrixConfigScreenCompat() { }

    static ClickableWidget button(UiRect bounds, Text label, Runnable action) {
        return new ButtonWidget(bounds.x(), bounds.y(), bounds.width(), bounds.height(), label,
                ignored -> action.run(), ButtonWidget.EMPTY);
    }

    static MutableText emptyText() { return new LiteralText(""); }
    static MutableText literalText(String value) { return new LiteralText(value); }
    static MutableText translatableText(String key, Object... arguments) {
        return new TranslatableText(key, arguments);
    }
}
