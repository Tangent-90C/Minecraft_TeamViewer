package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

final class FabricHudTextCompat {
    private FabricHudTextCompat() { }

    static Text toText(UiText value) {
        MutableText text = value.translationKey() == null
                ? Text.literal(value.literal() == null ? "" : value.literal())
                : Text.translatable(value.translationKey(),
                        value.arguments().stream().map(FabricHudTextCompat::toText).toArray());
        return value.suffix().isEmpty() ? text : text.append(value.suffix());
    }
}
