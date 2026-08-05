package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiAction;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerPainter;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerPainter.contains;
import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.*;

/** Dense JsMacros-inspired plugin manager host for NeoForge 1.21.8. */
public final class PluginManagerScreen extends Screen {
    private final Screen parent;
    private final PluginManagerUiController controller;
    private final Map<ConfigControlId, EditBox> fields = new HashMap<>();
    private PluginManagerView view;

    public PluginManagerScreen(Screen parent, PluginManagerUiController controller) {
        super(Component.translatable("screen.mc_teamviewer.integration_plugin.manager.title"));
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    protected void init() {
        fields.clear();
        view = controller.view(width, height);
        if (view.dialog() != null || (view.compact() && !view.compactDetail()) || view.detail() == null) return;
        for (SettingView setting : view.detail().settings()) {
            if (setting.kind() != SettingKind.TEXT || !intersects(setting.bounds(), view.detail().contentBounds())) continue;
            int inputWidth = Math.max(72, setting.bounds().width() * 43 / 100);
            int x = setting.bounds().x() + setting.bounds().width() - inputWidth - 5;
            int y = setting.bounds().y() + 7;
            EditBox field = new EditBox(font, x, y, inputWidth, 20, toComponent(setting.label()));
            field.setMaxLength(setting.maxLength());
            field.setValue(setting.value());
            field.setBordered(false);
            field.setTextColor(TEXT);
            field.setTextColorUneditable(MUTED);
            field.setResponder(value -> controller.setText(setting.id(), value));
            fields.put(setting.id(), field);
            addRenderableWidget(field);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, BACKDROP);
        view = controller.view(width, height);
        PluginManagerPainter.paint(view, new Canvas(graphics), mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);
        UiText tooltip = PluginManagerPainter.tooltipAt(view, mouseX, mouseY);
        if (tooltip != null) graphics.setTooltipForNextFrame(toComponent(tooltip), mouseX, mouseY);
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
        ConfigControlId id = PluginManagerPainter.actionAt(view, (int) mouseX, (int) mouseY);
        if (id == null) return false;
        activate(id);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (view.dialog() != null) return true;
        int direction = verticalAmount > 0 ? -1 : verticalAmount < 0 ? 1 : 0;
        if (direction == 0) return false;
        if ((!view.compact() || !view.compactDetail())
                && contains(view.listBounds(), (int) mouseX, (int) mouseY)) {
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
        if (action.type() == ConfigUiAction.Type.CLOSE_TO_PARENT) minecraft.setScreen(parent);
        else refresh();
    }

    private void refresh() {
        minecraft.setScreen(new PluginManagerScreen(parent, controller));
    }

    @Override
    public void onClose() {
        controller.commitTextSettings();
        minecraft.setScreen(parent);
    }

    private final class Canvas implements PluginManagerPainter.Canvas {
        private final GuiGraphics graphics;

        private Canvas(GuiGraphics graphics) {
            this.graphics = graphics;
        }

        @Override
        public void fill(UiRect bounds, int color) {
            graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                    bounds.y() + bounds.height(), color);
        }

        @Override
        public void text(UiText text, UiRect bounds, int color, boolean shadow) {
            Component component = toComponent(text);
            String value = component.getString();
            if (font.width(value) > bounds.width()) {
                value = font.plainSubstrByWidth(value, Math.max(0, bounds.width() - font.width("…"))) + "…";
            }
            graphics.drawString(font, value, bounds.x(), bounds.y(), color, shadow);
        }

        @Override
        public void centered(UiText text, int centerX, int y, int color) {
            Component component = toComponent(text);
            graphics.drawString(font, component, centerX - font.width(component) / 2, y, color, false);
        }

        @Override
        public void pushClip(UiRect bounds) {
            graphics.enableScissor(bounds.x(), bounds.y(),
                    bounds.x() + bounds.width(), bounds.y() + bounds.height());
        }

        @Override
        public void popClip() {
            graphics.disableScissor();
        }

        @Override
        public boolean textFieldFocused(ConfigControlId id) {
            EditBox field = fields.get(id);
            return field != null && field.isFocused();
        }
    }

    private static boolean intersects(UiRect first, UiRect second) {
        return first.x() < second.x() + second.width() && first.x() + first.width() > second.x()
                && first.y() < second.y() + second.height() && first.y() + first.height() > second.y();
    }

    private static Component toComponent(UiText value) {
        if (value == null) return Component.empty();
        MutableComponent component = value.translationKey() == null
                ? Component.literal(value.literal() == null ? "" : value.literal())
                : Component.translatable(value.translationKey(),
                        value.arguments().stream().map(PluginManagerScreen::toComponent).toArray());
        if (!value.suffix().isEmpty()) component.append(value.suffix());
        return component;
    }
}
