package fun.prof_chen.teamviewer.main_code.config.ui;

import java.util.List;

/**
 * Immutable, platform-neutral layout and state for the dense integration-plugin manager.
 * Minecraft adapters only draw these primitives and forward their action IDs.
 */
public record PluginManagerView(
        UiRect frame,
        UiRect header,
        UiRect titleBounds,
        ActionView installedTab,
        ActionView disabledTab,
        ActionView rescanAction,
        ActionView closeAction,
        UiRect listBounds,
        UiRect detailBounds,
        boolean compact,
        boolean compactDetail,
        PluginManagerTab tab,
        List<ListItemView> items,
        DetailView detail,
        MessageView message,
        DialogView dialog) {

    public static final int BACKDROP = 0xB011161C;
    public static final int FRAME = 0xF011161C;
    public static final int PANEL = 0xF01B232D;
    public static final int PANEL_ALT = 0xF0182029;
    public static final int SELECTED = 0xF025384A;
    public static final int HOVERED = 0xF0212C37;
    public static final int BORDER = 0xFF3A4652;
    public static final int ACCENT = 0xFF42C6D7;
    public static final int TEXT = 0xFFF2F5F7;
    public static final int MUTED = 0xFF9AA7B3;
    public static final int SUCCESS = 0xFF60D394;
    public static final int WARNING = 0xFFF1C75B;
    public static final int ERROR = 0xFFFF6B6B;
    public static final int DISABLED = 0xFF66717C;

    public PluginManagerView {
        items = List.copyOf(items == null ? List.of() : items);
    }

    public enum PluginManagerTab { INSTALLED, DISABLED }

    public enum SettingKind { BOOLEAN, ENUM, TEXT }

    public enum DialogKind { COPY_GUIDE, DELETE_CONFIRM }

    public record ActionView(
            ConfigControlId id,
            UiRect bounds,
            UiText label,
            UiText tooltip,
            boolean active,
            boolean selected,
            boolean danger) { }

    public record ListItemView(
            ConfigControlId selectId,
            ConfigControlId toggleId,
            UiRect bounds,
            UiRect toggleBounds,
            UiText name,
            UiText meta,
            UiText status,
            UiText tooltip,
            int statusColor,
            boolean selected,
            boolean enabled,
            boolean toggleVisible,
            boolean toggleActive) { }

    public record DetailView(
            UiRect bounds,
            UiRect contentBounds,
            ActionView compactBack,
            UiText title,
            UiText subtitle,
            UiText status,
            UiText summary,
            UiText diagnostic,
            int statusColor,
            UiText firstSectionTitle,
            UiText secondSectionTitle,
            List<LineView> lines,
            List<SettingView> settings,
            List<ActionView> actions,
            boolean disabledPlugin) {
        public DetailView {
            lines = List.copyOf(lines == null ? List.of() : lines);
            settings = List.copyOf(settings == null ? List.of() : settings);
            actions = List.copyOf(actions == null ? List.of() : actions);
        }
    }

    public record LineView(
            UiRect bounds,
            UiText primary,
            UiText secondary,
            UiText status,
            UiText tooltip,
            int statusColor) { }

    public record SettingView(
            ConfigControlId id,
            UiRect bounds,
            UiText label,
            UiText valueLabel,
            UiText tooltip,
            SettingKind kind,
            String value,
            int maxLength,
            boolean checked,
            boolean active) { }

    public record MessageView(
            UiRect bounds,
            UiText text,
            UiText tooltip,
            int color) { }

    public record DialogView(
            DialogKind kind,
            UiRect bounds,
            UiText title,
            List<UiText> lines,
            List<ActionView> actions) {
        public DialogView {
            lines = List.copyOf(lines == null ? List.of() : lines);
            actions = List.copyOf(actions == null ? List.of() : actions);
        }
    }
}
