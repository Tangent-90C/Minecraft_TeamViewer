package fun.prof_chen.teamviewer.main_code.config.ui;

import java.util.List;

import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.*;

/** Shared visual composition and hit-testing for native plugin-manager canvases. */
public final class PluginManagerPainter {
    private PluginManagerPainter() { }

    public interface Canvas {
        void fill(UiRect bounds, int color);

        void text(UiText text, UiRect bounds, int color, boolean shadow);

        void centered(UiText text, int centerX, int y, int color);

        void pushClip(UiRect bounds);

        void popClip();

        boolean textFieldFocused(ConfigControlId id);

        default void border(UiRect bounds, int color) {
            fill(new UiRect(bounds.x(), bounds.y(), bounds.width(), 1), color);
            fill(new UiRect(bounds.x(), bounds.y() + bounds.height() - 1, bounds.width(), 1), color);
            fill(new UiRect(bounds.x(), bounds.y(), 1, bounds.height()), color);
            fill(new UiRect(bounds.x() + bounds.width() - 1, bounds.y(), 1, bounds.height()), color);
        }
    }

    public static void paint(PluginManagerView view, Canvas canvas, int mouseX, int mouseY) {
        canvas.fill(view.frame(), FRAME);
        canvas.border(view.frame(), BORDER);
        canvas.fill(view.header(), PANEL_ALT);
        canvas.fill(new UiRect(view.header().x(), view.header().y() + view.header().height() - 1,
                view.header().width(), 1), BORDER);
        canvas.text(UiText.translatable("screen.mc_teamviewer.integration_plugin.manager.title"),
                view.titleBounds(), TEXT, true);
        action(canvas, view.installedTab(), mouseX, mouseY);
        action(canvas, view.disabledTab(), mouseX, mouseY);
        action(canvas, view.rescanAction(), mouseX, mouseY);
        action(canvas, view.closeAction(), mouseX, mouseY);
        if (!view.compact() || !view.compactDetail()) list(view, canvas, mouseX, mouseY);
        if (!view.compact() || view.compactDetail()) detail(view.detail(), canvas, mouseX, mouseY);
        if (view.message() != null) message(view.message(), canvas);
        if (view.dialog() != null) dialog(view, canvas, mouseX, mouseY);
    }

    private static void list(PluginManagerView view, Canvas canvas, int mouseX, int mouseY) {
        canvas.fill(view.listBounds(), PANEL);
        if (!view.compact()) {
            canvas.fill(new UiRect(view.listBounds().x() + view.listBounds().width(),
                    view.listBounds().y(), 1, view.listBounds().height()), BORDER);
        }
        canvas.pushClip(view.listBounds());
        for (ListItemView item : view.items()) {
            boolean hovered = contains(item.bounds(), mouseX, mouseY);
            canvas.fill(item.bounds(), item.selected() ? SELECTED : hovered ? HOVERED : PANEL);
            if (item.selected()) canvas.fill(
                    new UiRect(item.bounds().x(), item.bounds().y(), 2, item.bounds().height()), ACCENT);
            canvas.fill(new UiRect(item.bounds().x(), item.bounds().y() + item.bounds().height() - 1,
                    item.bounds().width(), 1), BORDER);
            canvas.fill(new UiRect(item.bounds().x() + 8, item.bounds().y() + 9, 4, 4),
                    item.statusColor());
            int right = item.toggleVisible() ? item.toggleBounds().x() - 6
                    : item.bounds().x() + item.bounds().width() - 8;
            canvas.text(item.name(), new UiRect(item.bounds().x() + 17, item.bounds().y() + 5,
                    Math.max(10, right - item.bounds().x() - 17), 10), TEXT, false);
            canvas.text(item.meta(), new UiRect(item.bounds().x() + 17, item.bounds().y() + 19,
                    Math.max(10, right - item.bounds().x() - 17), 10), MUTED, false);
            if (item.toggleVisible()) toggle(canvas, item.toggleBounds(), item.enabled(), item.toggleActive());
        }
        canvas.popClip();
        if (view.items().isEmpty()) {
            UiText empty = view.tab() == PluginManagerTab.INSTALLED
                    ? UiText.translatable("screen.mc_teamviewer.integration_plugin.none")
                    : UiText.translatable("screen.mc_teamviewer.integration_plugin.disabled_none");
            canvas.centered(empty, view.listBounds().x() + view.listBounds().width() / 2,
                    view.listBounds().y() + 20, MUTED);
        }
    }

    private static void detail(DetailView detail, Canvas canvas, int mouseX, int mouseY) {
        if (detail == null) return;
        canvas.fill(detail.bounds(), PANEL_ALT);
        if (detail.compactBack() != null) action(canvas, detail.compactBack(), mouseX, mouseY);
        int titleX = detail.compactBack() == null ? detail.bounds().x() + 12 : detail.bounds().x() + 76;
        int titleWidth = detail.bounds().x() + detail.bounds().width() - titleX - 12;
        canvas.text(detail.title(), new UiRect(titleX, detail.bounds().y() + 10, titleWidth, 12),
                TEXT, true);
        canvas.text(detail.subtitle(), new UiRect(titleX, detail.bounds().y() + 27, titleWidth, 10),
                MUTED, false);
        canvas.fill(new UiRect(titleX, detail.bounds().y() + 48, 4, 4), detail.statusColor());
        canvas.text(detail.status(), new UiRect(titleX + 8, detail.bounds().y() + 45,
                Math.max(10, titleWidth / 2), 10), detail.statusColor(), false);
        canvas.text(detail.summary(), new UiRect(titleX + titleWidth / 2, detail.bounds().y() + 45,
                Math.max(10, titleWidth / 2), 10), MUTED, false);
        canvas.fill(new UiRect(detail.bounds().x(), detail.bounds().y() + 66,
                detail.bounds().width(), 1), BORDER);

        canvas.pushClip(detail.contentBounds());
        if (!detail.lines().isEmpty() && detail.firstSectionTitle() != null) {
            LineView first = detail.lines().get(0);
            canvas.text(detail.firstSectionTitle(),
                    new UiRect(first.bounds().x(), first.bounds().y() - 15,
                            first.bounds().width(), 10), ACCENT, false);
        }
        for (LineView line : detail.lines()) line(canvas, line);
        if (!detail.settings().isEmpty() && detail.secondSectionTitle() != null) {
            SettingView first = detail.settings().get(0);
            canvas.text(detail.secondSectionTitle(),
                    new UiRect(first.bounds().x(), first.bounds().y() - 15,
                            first.bounds().width(), 10), ACCENT, false);
        }
        for (SettingView setting : detail.settings()) setting(canvas, setting, mouseX, mouseY);
        for (ActionView action : detail.actions()) action(canvas, action, mouseX, mouseY);
        canvas.popClip();
    }

    private static void line(Canvas canvas, LineView line) {
        canvas.fill(line.bounds(), PANEL);
        canvas.fill(new UiRect(line.bounds().x(), line.bounds().y() + line.bounds().height() - 1,
                line.bounds().width(), 1), BORDER);
        int statusWidth = Math.min(64, line.bounds().width() / 4);
        canvas.text(line.primary(), new UiRect(line.bounds().x() + 7, line.bounds().y() + 4,
                line.bounds().width() - statusWidth - 14, 10), TEXT, false);
        canvas.text(line.secondary(), new UiRect(line.bounds().x() + 7, line.bounds().y() + 16,
                line.bounds().width() - statusWidth - 14, 10), MUTED, false);
        canvas.text(line.status(), new UiRect(line.bounds().x() + line.bounds().width() - statusWidth,
                line.bounds().y() + 9, statusWidth - 7, 10), line.statusColor(), false);
    }

    private static void setting(Canvas canvas, SettingView setting, int mouseX, int mouseY) {
        canvas.fill(setting.bounds(), PANEL);
        canvas.fill(new UiRect(setting.bounds().x(), setting.bounds().y() + setting.bounds().height() - 1,
                setting.bounds().width(), 1), BORDER);
        int valueWidth = setting.kind() == SettingKind.TEXT
                ? setting.bounds().width() * 43 / 100 : Math.min(100, setting.bounds().width() / 3);
        canvas.text(setting.label(), new UiRect(setting.bounds().x() + 7, setting.bounds().y() + 9,
                setting.bounds().width() - valueWidth - 18, 10), TEXT, false);
        if (setting.kind() == SettingKind.BOOLEAN) {
            toggle(canvas, new UiRect(setting.bounds().x() + setting.bounds().width() - 36,
                    setting.bounds().y() + 7, 26, 14), setting.checked(), setting.active());
        } else if (setting.kind() == SettingKind.ENUM) {
            UiRect selector = new UiRect(setting.bounds().x() + setting.bounds().width() - valueWidth - 6,
                    setting.bounds().y() + 4, valueWidth, 20);
            canvas.fill(selector, contains(selector, mouseX, mouseY) ? HOVERED : PANEL_ALT);
            canvas.border(selector, setting.active() ? ACCENT : BORDER);
            canvas.centered(setting.valueLabel(), selector.x() + selector.width() / 2,
                    selector.y() + 6, TEXT);
        } else {
            UiRect field = new UiRect(setting.bounds().x() + setting.bounds().width() - valueWidth - 5,
                    setting.bounds().y() + 6, valueWidth, 21);
            canvas.fill(field, PANEL_ALT);
            canvas.border(field, canvas.textFieldFocused(setting.id()) ? ACCENT : BORDER);
        }
    }

    private static void action(Canvas canvas, ActionView action, int mouseX, int mouseY) {
        if (action == null) return;
        boolean hovered = action.active() && contains(action.bounds(), mouseX, mouseY);
        canvas.fill(action.bounds(), action.selected() ? SELECTED : hovered ? HOVERED : PANEL);
        canvas.border(action.bounds(), action.selected() ? ACCENT
                : action.danger() ? ERROR : action.active() ? BORDER : DISABLED);
        canvas.centered(action.label(), action.bounds().x() + action.bounds().width() / 2,
                action.bounds().y() + (action.bounds().height() - 8) / 2,
                !action.active() ? DISABLED : action.danger() ? ERROR : TEXT);
    }

    private static void toggle(Canvas canvas, UiRect bounds, boolean checked, boolean active) {
        canvas.fill(bounds, !active ? DISABLED : checked ? 0xFF277D88 : 0xFF39434D);
        canvas.border(bounds, active && checked ? ACCENT : BORDER);
        int knobX = checked ? bounds.x() + bounds.width() - 10 : bounds.x() + 3;
        canvas.fill(new UiRect(knobX, bounds.y() + 3, 7, bounds.height() - 6),
                active ? TEXT : MUTED);
    }

    private static void message(MessageView message, Canvas canvas) {
        canvas.fill(message.bounds(), PANEL);
        canvas.fill(new UiRect(message.bounds().x(), message.bounds().y(),
                3, message.bounds().height()), message.color());
        canvas.text(message.text(), new UiRect(message.bounds().x() + 9, message.bounds().y() + 7,
                message.bounds().width() - 18, 10), message.color(), false);
    }

    private static void dialog(PluginManagerView view, Canvas canvas, int mouseX, int mouseY) {
        DialogView dialog = view.dialog();
        canvas.fill(view.frame(), 0xB0000000);
        canvas.fill(dialog.bounds(), PANEL_ALT);
        canvas.border(dialog.bounds(), ACCENT);
        canvas.text(dialog.title(), new UiRect(dialog.bounds().x() + 12, dialog.bounds().y() + 11,
                dialog.bounds().width() - 24, 12), TEXT, true);
        int y = dialog.bounds().y() + 34;
        for (UiText line : dialog.lines()) {
            canvas.text(line, new UiRect(dialog.bounds().x() + 12, y,
                    dialog.bounds().width() - 24, 12), MUTED, false);
            y += 20;
        }
        for (ActionView action : dialog.actions()) action(canvas, action, mouseX, mouseY);
    }

    public static ConfigControlId actionAt(PluginManagerView view, int x, int y) {
        if (view.dialog() != null) {
            for (ActionView action : view.dialog().actions()) {
                if (action.active() && contains(action.bounds(), x, y)) return action.id();
            }
            return null;
        }
        for (ActionView action : List.of(view.installedTab(), view.disabledTab(),
                view.rescanAction(), view.closeAction())) {
            if (action.active() && contains(action.bounds(), x, y)) return action.id();
        }
        if (!view.compact() || !view.compactDetail()) {
            for (ListItemView item : view.items()) {
                if (item.toggleVisible() && item.toggleActive() && contains(item.toggleBounds(), x, y)) {
                    return item.toggleId();
                }
                if (contains(item.bounds(), x, y)) return item.selectId();
            }
        }
        if ((!view.compact() || view.compactDetail()) && view.detail() != null) {
            DetailView detail = view.detail();
            if (detail.compactBack() != null && contains(detail.compactBack().bounds(), x, y)) {
                return detail.compactBack().id();
            }
            for (SettingView setting : detail.settings()) {
                if (setting.kind() != SettingKind.TEXT && setting.active()
                        && contains(setting.bounds(), x, y)) return setting.id();
            }
            for (ActionView action : detail.actions()) {
                if (action.active() && contains(action.bounds(), x, y)) return action.id();
            }
        }
        return null;
    }

    public static UiText tooltipAt(PluginManagerView view, int x, int y) {
        if (view.dialog() != null) {
            for (ActionView action : view.dialog().actions()) {
                if (action.tooltip() != null && contains(action.bounds(), x, y)) return action.tooltip();
            }
            return null;
        }
        for (ListItemView item : view.items()) {
            if (item.tooltip() != null && contains(item.bounds(), x, y)) return item.tooltip();
        }
        if (view.detail() != null) {
            DetailView detail = view.detail();
            if (detail.diagnostic() != null && contains(
                    new UiRect(detail.bounds().x(), detail.bounds().y(), detail.bounds().width(), 66), x, y)) {
                return detail.diagnostic();
            }
            for (LineView line : detail.lines()) {
                if (line.tooltip() != null && contains(line.bounds(), x, y)) return line.tooltip();
            }
            for (ActionView action : detail.actions()) {
                if (action.tooltip() != null && contains(action.bounds(), x, y)) return action.tooltip();
            }
        }
        return view.message() != null && view.message().tooltip() != null
                && contains(view.message().bounds(), x, y) ? view.message().tooltip() : null;
    }

    public static boolean contains(UiRect rect, int x, int y) {
        return rect != null && x >= rect.x() && x < rect.x() + rect.width()
                && y >= rect.y() && y < rect.y() + rect.height();
    }
}
