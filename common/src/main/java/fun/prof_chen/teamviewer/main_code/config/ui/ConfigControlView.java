package fun.prof_chen.teamviewer.main_code.config.ui;

/** Immutable control description consumed by version-specific screen hosts. */
public record ConfigControlView(
        ConfigControlId id,
        ConfigControlKind kind,
        UiRect bounds,
        UiRect labelBounds,
        UiText label,
        UiText hint,
        UiText tooltip,
        String value,
        int maxLength,
        boolean checked,
        boolean active,
        boolean visible,
        int color,
        TextAlignment alignment) {

    public enum TextAlignment { LEFT, CENTER }

    public static ConfigControlView textField(
            ConfigControlId id, UiRect bounds, UiRect labelBounds, UiText label, UiText hint,
            UiText tooltip, String value, int maxLength) {
        return textField(id, bounds, labelBounds, label, hint, tooltip, value, maxLength, true);
    }

    public static ConfigControlView textField(
            ConfigControlId id, UiRect bounds, UiRect labelBounds, UiText label, UiText hint,
            UiText tooltip, String value, int maxLength, boolean active) {
        return new ConfigControlView(id, ConfigControlKind.TEXT_FIELD, bounds, labelBounds, label, hint,
                tooltip, value, maxLength, false, active, true, 0xFFFFFF, TextAlignment.LEFT);
    }

    public static ConfigControlView button(
            ConfigControlId id, UiRect bounds, UiText label, UiText tooltip, boolean active) {
        return new ConfigControlView(id, ConfigControlKind.BUTTON, bounds, null, label, null, tooltip,
                null, 0, false, active, true, 0xFFFFFF, TextAlignment.CENTER);
    }

    public static ConfigControlView checkbox(
            ConfigControlId id, UiRect bounds, UiText label, UiText tooltip, boolean checked) {
        return checkbox(id, bounds, label, tooltip, checked, true);
    }

    public static ConfigControlView checkbox(
            ConfigControlId id, UiRect bounds, UiText label, UiText tooltip,
            boolean checked, boolean active) {
        return new ConfigControlView(id, ConfigControlKind.CHECKBOX, bounds, null, label, null, tooltip,
                null, 0, checked, active, true, 0xFFFFFF, TextAlignment.LEFT);
    }

    public static ConfigControlView text(
            ConfigControlId id, UiRect bounds, UiText label, UiText tooltip, int color,
            boolean visible, TextAlignment alignment) {
        return new ConfigControlView(id, ConfigControlKind.TEXT, bounds, null, label, null, tooltip,
                null, 0, false, false, visible, color, alignment);
    }
}
