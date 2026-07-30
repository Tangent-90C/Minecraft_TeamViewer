package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiAction;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.ActionView;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.DetailView;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.DialogView;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.LineView;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.ListItemView;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.MessageView;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.PluginManagerTab;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.SettingKind;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.SettingView;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.*;

/** Dense JsMacros-inspired plugin manager host for Fabric/Yarn 1.21.8. */
public final class PluginManagerScreen extends Screen {
    private final Screen parent;
    private final PluginManagerUiController controller;
    private final Map<ConfigControlId, TextFieldWidget> fields = new HashMap<>();
    private PluginManagerView view;

    public PluginManagerScreen(Screen parent, PluginManagerUiController controller) {
        super(Text.translatable("screen.mc_teamviewer.integration_plugin.manager.title"));
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    protected void init() {
        super.init();
        fields.clear();
        view = controller.view(width, height);
        if (view.dialog() != null || (view.compact() && !view.compactDetail()) || view.detail() == null) return;
        for (SettingView setting : view.detail().settings()) {
            if (setting.kind() != SettingKind.TEXT || !intersects(setting.bounds(), view.detail().contentBounds())) continue;
            int inputWidth = Math.max(72, setting.bounds().width() * 43 / 100);
            int x = setting.bounds().x() + setting.bounds().width() - inputWidth - 5;
            int y = setting.bounds().y() + 7;
            TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, inputWidth, 20, toText(setting.label()));
            field.setMaxLength(setting.maxLength());
            field.setText(setting.value());
            field.setDrawsBackground(false);
            field.setEditableColor(TEXT & 0xFFFFFF);
            field.setUneditableColor(MUTED & 0xFFFFFF);
            field.setChangedListener(value -> controller.setText(setting.id(), value));
            fields.put(setting.id(), field);
            addDrawableChild(field);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKDROP);
        view = controller.view(width, height);
        drawManager(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        if (view.message() != null) drawMessage(context, view.message());
        if (view.dialog() != null) drawDialog(context, view.dialog(), mouseX, mouseY);
        UiText tooltip = tooltipAt(view, mouseX, mouseY);
        if (tooltip != null) context.drawTooltip(textRenderer, List.of(toText(tooltip)), mouseX, mouseY);
    }

    private void drawManager(DrawContext context, int mouseX, int mouseY) {
        fill(context, view.frame(), FRAME);
        border(context, view.frame(), BORDER);
        fill(context, view.header(), PANEL_ALT);
        context.fill(view.header().x(), view.header().y() + view.header().height() - 1,
                view.header().x() + view.header().width(), view.header().y() + view.header().height(), BORDER);
        drawClipped(context, Text.translatable("screen.mc_teamviewer.integration_plugin.manager.title"),
                view.titleBounds(), TEXT, true);
        drawAction(context, view.installedTab(), mouseX, mouseY);
        drawAction(context, view.disabledTab(), mouseX, mouseY);
        drawAction(context, view.rescanAction(), mouseX, mouseY);
        drawAction(context, view.closeAction(), mouseX, mouseY);

        if (!view.compact() || !view.compactDetail()) drawList(context, mouseX, mouseY);
        if (!view.compact() || view.compactDetail()) drawDetail(context, mouseX, mouseY);
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        fill(context, view.listBounds(), PANEL);
        if (!view.compact()) {
            context.fill(view.listBounds().x() + view.listBounds().width(), view.listBounds().y(),
                    view.listBounds().x() + view.listBounds().width() + 1,
                    view.listBounds().y() + view.listBounds().height(), BORDER);
        }
        context.enableScissor(view.listBounds().x(), view.listBounds().y(),
                view.listBounds().x() + view.listBounds().width(),
                view.listBounds().y() + view.listBounds().height());
        for (ListItemView item : view.items()) drawListItem(context, item, mouseX, mouseY);
        context.disableScissor();
        if (view.items().isEmpty()) {
            UiText empty = view.tab() == PluginManagerTab.INSTALLED
                    ? UiText.translatable("screen.mc_teamviewer.integration_plugin.none")
                    : UiText.translatable("screen.mc_teamviewer.integration_plugin.disabled_none");
            drawCentered(context, empty, view.listBounds().x() + view.listBounds().width() / 2,
                    view.listBounds().y() + 20, MUTED);
        }
    }

    private void drawListItem(DrawContext context, ListItemView item, int mouseX, int mouseY) {
        boolean hovered = contains(item.bounds(), mouseX, mouseY);
        fill(context, item.bounds(), item.selected() ? SELECTED : hovered ? HOVERED : PANEL);
        if (item.selected()) {
            context.fill(item.bounds().x(), item.bounds().y(), item.bounds().x() + 2,
                    item.bounds().y() + item.bounds().height(), ACCENT);
        }
        context.fill(item.bounds().x(), item.bounds().y() + item.bounds().height() - 1,
                item.bounds().x() + item.bounds().width(), item.bounds().y() + item.bounds().height(), BORDER);
        context.fill(item.bounds().x() + 8, item.bounds().y() + 9,
                item.bounds().x() + 12, item.bounds().y() + 13, item.statusColor());
        int right = item.toggleVisible() ? item.toggleBounds().x() - 6
                : item.bounds().x() + item.bounds().width() - 8;
        drawClipped(context, toText(item.name()),
                new UiRect(item.bounds().x() + 17, item.bounds().y() + 5,
                        Math.max(10, right - item.bounds().x() - 17), 10), TEXT, false);
        drawClipped(context, toText(item.meta()),
                new UiRect(item.bounds().x() + 17, item.bounds().y() + 19,
                        Math.max(10, right - item.bounds().x() - 17), 10), MUTED, false);
        if (item.toggleVisible()) drawSwitch(context, item.toggleBounds(), item.enabled(), item.toggleActive());
    }

    private void drawDetail(DrawContext context, int mouseX, int mouseY) {
        DetailView detail = view.detail();
        fill(context, detail.bounds(), PANEL_ALT);
        if (detail.compactBack() != null) drawAction(context, detail.compactBack(), mouseX, mouseY);
        int titleX = detail.compactBack() == null ? detail.bounds().x() + 12 : detail.bounds().x() + 76;
        int titleWidth = detail.bounds().x() + detail.bounds().width() - titleX - 12;
        drawClipped(context, toText(detail.title()),
                new UiRect(titleX, detail.bounds().y() + 10, titleWidth, 12), TEXT, true);
        drawClipped(context, toText(detail.subtitle()),
                new UiRect(titleX, detail.bounds().y() + 27, titleWidth, 10), MUTED, false);
        context.fill(titleX, detail.bounds().y() + 48, titleX + 4, detail.bounds().y() + 52,
                detail.statusColor());
        drawClipped(context, toText(detail.status()),
                new UiRect(titleX + 8, detail.bounds().y() + 45, Math.max(10, titleWidth / 2), 10),
                detail.statusColor(), false);
        drawClipped(context, toText(detail.summary()),
                new UiRect(titleX + titleWidth / 2, detail.bounds().y() + 45,
                        Math.max(10, titleWidth / 2), 10), MUTED, false);
        context.fill(detail.bounds().x(), detail.bounds().y() + 66,
                detail.bounds().x() + detail.bounds().width(), detail.bounds().y() + 67, BORDER);

        context.enableScissor(detail.contentBounds().x(), detail.contentBounds().y(),
                detail.contentBounds().x() + detail.contentBounds().width(),
                detail.contentBounds().y() + detail.contentBounds().height());
        if (!detail.lines().isEmpty() && detail.firstSectionTitle() != null) {
            LineView first = detail.lines().get(0);
            drawClipped(context, toText(detail.firstSectionTitle()),
                    new UiRect(first.bounds().x(), first.bounds().y() - 15, first.bounds().width(), 10),
                    ACCENT, false);
        }
        for (LineView line : detail.lines()) drawLine(context, line);
        if (!detail.settings().isEmpty() && detail.secondSectionTitle() != null) {
            SettingView first = detail.settings().get(0);
            drawClipped(context, toText(detail.secondSectionTitle()),
                    new UiRect(first.bounds().x(), first.bounds().y() - 15, first.bounds().width(), 10),
                    ACCENT, false);
        }
        for (SettingView setting : detail.settings()) drawSetting(context, setting, mouseX, mouseY);
        for (ActionView action : detail.actions()) drawAction(context, action, mouseX, mouseY);
        context.disableScissor();
    }

    private void drawLine(DrawContext context, LineView line) {
        fill(context, line.bounds(), PANEL);
        context.fill(line.bounds().x(), line.bounds().y() + line.bounds().height() - 1,
                line.bounds().x() + line.bounds().width(),
                line.bounds().y() + line.bounds().height(), BORDER);
        int statusWidth = Math.min(64, line.bounds().width() / 4);
        drawClipped(context, toText(line.primary()),
                new UiRect(line.bounds().x() + 7, line.bounds().y() + 4,
                        line.bounds().width() - statusWidth - 14, 10), TEXT, false);
        drawClipped(context, toText(line.secondary()),
                new UiRect(line.bounds().x() + 7, line.bounds().y() + 16,
                        line.bounds().width() - statusWidth - 14, 10), MUTED, false);
        drawClipped(context, toText(line.status()),
                new UiRect(line.bounds().x() + line.bounds().width() - statusWidth,
                        line.bounds().y() + 9, statusWidth - 7, 10), line.statusColor(), false);
    }

    private void drawSetting(DrawContext context, SettingView setting, int mouseX, int mouseY) {
        fill(context, setting.bounds(), PANEL);
        context.fill(setting.bounds().x(), setting.bounds().y() + setting.bounds().height() - 1,
                setting.bounds().x() + setting.bounds().width(),
                setting.bounds().y() + setting.bounds().height(), BORDER);
        int valueWidth = setting.kind() == SettingKind.TEXT
                ? setting.bounds().width() * 43 / 100 : Math.min(100, setting.bounds().width() / 3);
        drawClipped(context, toText(setting.label()),
                new UiRect(setting.bounds().x() + 7, setting.bounds().y() + 9,
                        setting.bounds().width() - valueWidth - 18, 10), TEXT, false);
        if (setting.kind() == SettingKind.BOOLEAN) {
            UiRect toggle = new UiRect(setting.bounds().x() + setting.bounds().width() - 36,
                    setting.bounds().y() + 7, 26, 14);
            drawSwitch(context, toggle, setting.checked(), setting.active());
        } else if (setting.kind() == SettingKind.ENUM) {
            UiRect selector = new UiRect(setting.bounds().x() + setting.bounds().width() - valueWidth - 6,
                    setting.bounds().y() + 4, valueWidth, 20);
            fill(context, selector, contains(selector, mouseX, mouseY) ? HOVERED : PANEL_ALT);
            border(context, selector, setting.active() ? ACCENT : BORDER);
            drawCentered(context, setting.valueLabel(), selector.x() + selector.width() / 2,
                    selector.y() + 6, TEXT);
        } else {
            UiRect field = new UiRect(setting.bounds().x() + setting.bounds().width() - valueWidth - 5,
                    setting.bounds().y() + 6, valueWidth, 21);
            fill(context, field, PANEL_ALT);
            border(context, field, fields.get(setting.id()) != null && fields.get(setting.id()).isFocused()
                    ? ACCENT : BORDER);
        }
    }

    private void drawAction(DrawContext context, ActionView action, int mouseX, int mouseY) {
        if (action == null) return;
        boolean hovered = action.active() && contains(action.bounds(), mouseX, mouseY);
        int background = action.selected() ? SELECTED : hovered ? HOVERED : PANEL;
        fill(context, action.bounds(), background);
        border(context, action.bounds(), action.selected() ? ACCENT
                : action.danger() ? ERROR : action.active() ? BORDER : DISABLED);
        int color = !action.active() ? DISABLED : action.danger() ? ERROR : TEXT;
        drawCentered(context, action.label(), action.bounds().x() + action.bounds().width() / 2,
                action.bounds().y() + (action.bounds().height() - 8) / 2, color);
    }

    private void drawSwitch(DrawContext context, UiRect bounds, boolean checked, boolean active) {
        int track = !active ? DISABLED : checked ? 0xFF277D88 : 0xFF39434D;
        fill(context, bounds, track);
        border(context, bounds, active && checked ? ACCENT : BORDER);
        int knobX = checked ? bounds.x() + bounds.width() - 10 : bounds.x() + 3;
        context.fill(knobX, bounds.y() + 3, knobX + 7, bounds.y() + bounds.height() - 3,
                active ? TEXT : MUTED);
    }

    private void drawMessage(DrawContext context, MessageView message) {
        fill(context, message.bounds(), PANEL);
        context.fill(message.bounds().x(), message.bounds().y(),
                message.bounds().x() + 3, message.bounds().y() + message.bounds().height(), message.color());
        drawClipped(context, toText(message.text()),
                new UiRect(message.bounds().x() + 9, message.bounds().y() + 7,
                        message.bounds().width() - 18, 10), message.color(), false);
    }

    private void drawDialog(DrawContext context, DialogView dialog, int mouseX, int mouseY) {
        context.fill(view.frame().x(), view.frame().y(),
                view.frame().x() + view.frame().width(),
                view.frame().y() + view.frame().height(), 0xB0000000);
        fill(context, dialog.bounds(), PANEL_ALT);
        border(context, dialog.bounds(), ACCENT);
        drawClipped(context, toText(dialog.title()),
                new UiRect(dialog.bounds().x() + 12, dialog.bounds().y() + 11,
                        dialog.bounds().width() - 24, 12), TEXT, true);
        int y = dialog.bounds().y() + 34;
        for (UiText line : dialog.lines()) {
            drawClipped(context, toText(line),
                    new UiRect(dialog.bounds().x() + 12, y, dialog.bounds().width() - 24, 12),
                    MUTED, false);
            y += 20;
        }
        for (ActionView action : dialog.actions()) drawAction(context, action, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = button == 0 && super.mouseClicked(mouseX, mouseY, button);
        if (focusedBefore != null && !focusedBefore.equals(focusedField())) {
            controller.commitTextSettings();
        }
        if (handledByField) return true;
        if (button != 0) return false;
        ConfigControlId action = actionAt(view, (int) mouseX, (int) mouseY);
        if (action == null) return false;
        activate(action);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (view.dialog() != null) return true;
        int direction = verticalAmount > 0 ? -1 : verticalAmount < 0 ? 1 : 0;
        if (direction == 0) return false;
        if ((!view.compact() || !view.compactDetail()) && contains(view.listBounds(), (int) mouseX, (int) mouseY)) {
            controller.scrollList(direction);
        } else if ((!view.compact() || view.compactDetail())
                && contains(view.detailBounds(), (int) mouseX, (int) mouseY)) {
            controller.scrollDetail(direction * 24);
        } else {
            return false;
        }
        refresh();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = super.keyPressed(keyCode, scanCode, modifiers);
        if (focusedBefore != null && !focusedBefore.equals(focusedField())) {
            controller.commitTextSettings();
        }
        if (handledByField) return true;
        if (view.dialog() == null && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            controller.commitTextSettings();
            controller.moveSelection(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            refresh();
            return true;
        }
        return false;
    }

    private ConfigControlId focusedField() {
        return fields.entrySet().stream()
                .filter(entry -> entry.getValue().isFocused())
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private void activate(ConfigControlId id) {
        ConfigUiAction action = controller.activate(id);
        if (action.type() == ConfigUiAction.Type.CLOSE_TO_PARENT) {
            MinecraftClient.getInstance().setScreen(parent);
        } else {
            refresh();
        }
    }

    private void refresh() {
        MinecraftClient.getInstance().setScreen(new PluginManagerScreen(parent, controller));
    }

    @Override
    public void close() {
        controller.commitTextSettings();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private ConfigControlId actionAt(PluginManagerView current, int x, int y) {
        if (current.dialog() != null) {
            for (ActionView action : current.dialog().actions()) {
                if (action.active() && contains(action.bounds(), x, y)) return action.id();
            }
            return null;
        }
        for (ActionView action : List.of(current.installedTab(), current.disabledTab(),
                current.rescanAction(), current.closeAction())) {
            if (action.active() && contains(action.bounds(), x, y)) return action.id();
        }
        if (!current.compact() || !current.compactDetail()) {
            for (ListItemView item : current.items()) {
                if (item.toggleVisible() && item.toggleActive() && contains(item.toggleBounds(), x, y)) {
                    return item.toggleId();
                }
                if (contains(item.bounds(), x, y)) return item.selectId();
            }
        }
        if ((!current.compact() || current.compactDetail()) && current.detail() != null) {
            DetailView detail = current.detail();
            if (detail.compactBack() != null && contains(detail.compactBack().bounds(), x, y)) {
                return detail.compactBack().id();
            }
            for (SettingView setting : detail.settings()) {
                if (setting.kind() != SettingKind.TEXT && setting.active() && contains(setting.bounds(), x, y)) {
                    return setting.id();
                }
            }
            for (ActionView action : detail.actions()) {
                if (action.active() && contains(action.bounds(), x, y)) return action.id();
            }
        }
        return null;
    }

    private UiText tooltipAt(PluginManagerView current, int x, int y) {
        if (current.dialog() != null) {
            for (ActionView action : current.dialog().actions()) {
                if (action.tooltip() != null && contains(action.bounds(), x, y)) return action.tooltip();
            }
            return null;
        }
        for (ListItemView item : current.items()) {
            if (item.tooltip() != null && contains(item.bounds(), x, y)) return item.tooltip();
        }
        if (current.detail() != null) {
            if (current.detail().diagnostic() != null && contains(
                    new UiRect(current.detail().bounds().x(), current.detail().bounds().y(),
                            current.detail().bounds().width(), 66), x, y)) return current.detail().diagnostic();
            for (LineView line : current.detail().lines()) {
                if (line.tooltip() != null && contains(line.bounds(), x, y)) return line.tooltip();
            }
            for (ActionView action : current.detail().actions()) {
                if (action.tooltip() != null && contains(action.bounds(), x, y)) return action.tooltip();
            }
        }
        return current.message() != null && current.message().tooltip() != null
                && contains(current.message().bounds(), x, y) ? current.message().tooltip() : null;
    }

    private void drawClipped(DrawContext context, Text text, UiRect bounds, int color, boolean shadow) {
        String value = text.getString();
        if (textRenderer.getWidth(value) > bounds.width()) {
            value = textRenderer.trimToWidth(value, Math.max(0, bounds.width() - textRenderer.getWidth("…"))) + "…";
        }
        context.drawText(textRenderer, value, bounds.x(), bounds.y(), color, shadow);
    }

    private void drawCentered(DrawContext context, UiText text, int centerX, int y, int color) {
        Text value = toText(text);
        context.drawText(textRenderer, value, centerX - textRenderer.getWidth(value) / 2, y, color, false);
    }

    private static void fill(DrawContext context, UiRect rect, int color) {
        context.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), color);
    }

    private static void border(DrawContext context, UiRect rect, int color) {
        context.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + 1, color);
        context.fill(rect.x(), rect.y() + rect.height() - 1,
                rect.x() + rect.width(), rect.y() + rect.height(), color);
        context.fill(rect.x(), rect.y(), rect.x() + 1, rect.y() + rect.height(), color);
        context.fill(rect.x() + rect.width() - 1, rect.y(),
                rect.x() + rect.width(), rect.y() + rect.height(), color);
    }

    private static boolean contains(UiRect rect, int x, int y) {
        return rect != null && x >= rect.x() && x < rect.x() + rect.width()
                && y >= rect.y() && y < rect.y() + rect.height();
    }

    private static boolean intersects(UiRect first, UiRect second) {
        return first.x() < second.x() + second.width() && first.x() + first.width() > second.x()
                && first.y() < second.y() + second.height() && first.y() + first.height() > second.y();
    }

    private static Text toText(UiText value) {
        if (value == null) return Text.empty();
        MutableText text = value.translationKey() == null
                ? Text.literal(value.literal() == null ? "" : value.literal())
                : Text.translatable(value.translationKey(),
                        value.arguments().stream().map(PluginManagerScreen::toText).toArray());
        if (!value.suffix().isEmpty()) text.append(value.suffix());
        return text;
    }
}
