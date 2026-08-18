package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiAction;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerPainter;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerPainter.contains;
import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.*;

/** Shared plugin manager host for the Fabric MatrixStack screen family. */
abstract class AbstractPluginManagerScreen extends Screen {
    protected final Screen parent;
    protected final PluginManagerUiController controller;
    private final Map<ConfigControlId, TextFieldWidget> fields = new HashMap<>();
    private PluginManagerView view;

    protected AbstractPluginManagerScreen(Screen parent, PluginManagerUiController controller) {
        super(FabricPluginScreenCompat.title());
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
            if (setting.kind() != SettingKind.TEXT
                    || !setting.bounds().intersects(view.detail().contentBounds())) continue;
            int inputWidth = Math.max(72, setting.bounds().width() * 43 / 100);
            int x = setting.bounds().x() + setting.bounds().width() - inputWidth - 5;
            int y = setting.bounds().y() + 7;
            TextFieldWidget field = new TextFieldWidget(
                    textRenderer, x, y, inputWidth, 20, FabricPluginScreenCompat.toText(setting.label()));
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
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        DrawableHelper.fill(context, 0, 0, width, height, BACKDROP);
        view = controller.view(width, height);
        PluginManagerPainter.paint(view, new Canvas(context), mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        UiText tooltip = PluginManagerPainter.tooltipAt(view, mouseX, mouseY);
        if (tooltip != null) {
            renderTooltip(context, List.of(FabricPluginScreenCompat.toText(tooltip)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = button == 0 && super.mouseClicked(mouseX, mouseY, button);
        if (focusedBefore != null && !focusedBefore.equals(focusedField())) controller.commitTextSettings();
        if (handledByField) return true;
        if (button != 0) return false;
        ConfigControlId id = PluginManagerPainter.actionAt(view, (int) mouseX, (int) mouseY);
        if (id == null) return false;
        activate(id);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
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
        if (focusedBefore != null && !focusedBefore.equals(focusedField())) controller.commitTextSettings();
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
        return fields.entrySet().stream().filter(entry -> entry.getValue().isFocused())
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private void activate(ConfigControlId id) {
        ConfigUiAction action = controller.activate(id);
        if (action.type() == ConfigUiAction.Type.CLOSE_TO_PARENT) closeToParent();
        else refresh();
    }

    private void refresh() {
        MinecraftClient.getInstance().setScreen(recreate());
    }

    protected abstract Screen recreate();

    protected final void closeToParent() {
        controller.commitTextSettings();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private final class Canvas implements PluginManagerPainter.Canvas {
        private final MatrixStack context;

        private Canvas(MatrixStack context) {
            this.context = context;
        }

        @Override
        public void fill(UiRect bounds, int color) {
            DrawableHelper.fill(context, bounds.x(), bounds.y(),
                    bounds.x() + bounds.width(), bounds.y() + bounds.height(), color);
        }

        @Override
        public void text(UiText text, UiRect bounds, int color, boolean shadow) {
            String value = FabricPluginScreenCompat.toText(text).getString();
            if (textRenderer.getWidth(value) > bounds.width()) {
                value = textRenderer.trimToWidth(
                        value, Math.max(0, bounds.width() - textRenderer.getWidth("…"))) + "…";
            }
            if (shadow) textRenderer.drawWithShadow(context, value, bounds.x(), bounds.y(), color);
            else textRenderer.draw(context, value, bounds.x(), bounds.y(), color);
        }

        @Override
        public void wrapped(UiText text, UiRect bounds, int color) {
            String remaining = FabricPluginScreenCompat.toText(text).getString();
            for (int y = bounds.y(); !remaining.isEmpty() && y + 9 <= bounds.y() + bounds.height(); y += 11) {
                String line = textRenderer.trimToWidth(remaining, bounds.width());
                if (line.isEmpty()) break;
                textRenderer.draw(context, line, bounds.x(), y, color);
                remaining = remaining.substring(line.length()).stripLeading();
            }
        }

        @Override
        public void centered(UiText text, int centerX, int y, int color) {
            Text value = FabricPluginScreenCompat.toText(text);
            textRenderer.draw(context, value, centerX - textRenderer.getWidth(value) / 2, y, color);
        }

        @Override
        public void pushClip(UiRect bounds) {
            double scale = client.getWindow().getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                    (int) Math.floor(bounds.x() * scale),
                    (int) Math.floor((height - bounds.y() - bounds.height()) * scale),
                    (int) Math.ceil(bounds.width() * scale),
                    (int) Math.ceil(bounds.height() * scale));
        }

        @Override
        public void popClip() {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        @Override
        public boolean textFieldFocused(ConfigControlId id) {
            TextFieldWidget field = fields.get(id);
            return field != null && field.isFocused();
        }
    }
}
