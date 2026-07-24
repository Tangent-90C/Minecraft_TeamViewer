package fun.prof_chen.teamviewer.main_code.config.ui;

import java.util.List;

/** Platform-neutral literal or translatable text tree. */
public record UiText(String literal, String translationKey, List<UiText> arguments, String suffix) {
    public UiText {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        suffix = suffix == null ? "" : suffix;
    }

    public static UiText literal(String value) {
        return new UiText(value == null ? "" : value, null, List.of(), "");
    }

    public static UiText translatable(String key, UiText... arguments) {
        return new UiText(null, key, arguments == null ? List.of() : List.of(arguments), "");
    }

    public static UiText toggle(String key, boolean enabled) {
        return new UiText(null, key, List.of(), enabled ? " [ON]" : " [OFF]");
    }

    public UiText append(String suffix) {
        return new UiText(literal, translationKey, arguments, this.suffix + (suffix == null ? "" : suffix));
    }
}
