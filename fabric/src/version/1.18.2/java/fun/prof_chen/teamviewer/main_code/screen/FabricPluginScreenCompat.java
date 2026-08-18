package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

final class FabricPluginScreenCompat {
    private FabricPluginScreenCompat() { }

    static Text title() {
        return new TranslatableText("screen.mc_teamviewer.integration_plugin.manager.title");
    }

    static Text toText(UiText value) {
        if (value == null) return new LiteralText("");
        MutableText text = value.translationKey() == null
                ? new LiteralText(value.literal() == null ? "" : value.literal())
                : new TranslatableText(value.translationKey(),
                        value.arguments().stream().map(FabricPluginScreenCompat::toText).toArray());
        if (!value.suffix().isEmpty()) text.append(value.suffix());
        return text;
    }
}
