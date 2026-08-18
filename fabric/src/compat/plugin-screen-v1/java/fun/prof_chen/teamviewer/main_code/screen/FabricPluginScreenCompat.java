package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

final class FabricPluginScreenCompat {
    private FabricPluginScreenCompat() { }

    static Text title() {
        return Text.translatable("screen.mc_teamviewer.integration_plugin.manager.title");
    }

    static Text toText(UiText value) {
        if (value == null) return Text.empty();
        MutableText text = value.translationKey() == null
                ? Text.literal(value.literal() == null ? "" : value.literal())
                : Text.translatable(value.translationKey(),
                        value.arguments().stream().map(FabricPluginScreenCompat::toText).toArray());
        if (!value.suffix().isEmpty()) text.append(value.suffix());
        return text;
    }
}
