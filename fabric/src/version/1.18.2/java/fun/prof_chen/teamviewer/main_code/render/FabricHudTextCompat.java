package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

final class FabricHudTextCompat {
    private FabricHudTextCompat() { }

    static Text toText(UiText value) {
        MutableText text = value.translationKey() == null
                ? new LiteralText(value.literal() == null ? "" : value.literal())
                : new TranslatableText(value.translationKey(),
                        value.arguments().stream().map(FabricHudTextCompat::toText).toArray());
        return value.suffix().isEmpty() ? text : text.append(value.suffix());
    }
}
