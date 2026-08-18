package fun.prof_chen.teamviewer.main_code.client.bridge;

import net.minecraft.text.Style;
import net.minecraft.text.TextColor;

import java.util.Locale;

final class FabricScoreboardTextSupport {
    private FabricScoreboardTextSupport() { }

    static String normalizeColor(Style style) {
        TextColor color = style == null ? null : style.getColor();
        if (color == null) return "#FFFFFF";
        String name = color.getName();
        if (name != null && !name.isBlank()) {
            return name.startsWith("#") ? name.toUpperCase(Locale.ROOT) : name.toLowerCase(Locale.ROOT);
        }
        return String.format("#%06X", color.getRgb() & 0xFFFFFF);
    }
}
