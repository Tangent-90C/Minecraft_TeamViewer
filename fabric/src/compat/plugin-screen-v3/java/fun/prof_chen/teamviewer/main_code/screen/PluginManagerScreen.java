package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiAction;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerPainter;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerPainter.contains;
import static fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerView.*;

/** Plugin manager host for the Fabric 1.21.9-1.21.11 input-event family. */
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
            if (setting.kind() != SettingKind.TEXT
                    || !intersects(setting.bounds(), view.detail().contentBounds())) continue;
            int inputWidth = Math.max(72, setting.bounds().width() * 43 / 100);
            int x = setting.bounds().x() + setting.bounds().width() - inputWidth - 5;
            int y = setting.bounds().y() + 7;
            TextFieldWidget field = new TextFieldWidget(
                    textRenderer, x, y, inputWidth, 20, toText(setting.label()));
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
        PluginManagerPainter.paint(view, new Canvas(context), mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        UiText tooltip = PluginManagerPainter.tooltipAt(view, mouseX, mouseY);
        if (tooltip != null) {
            context.drawTooltip(textRenderer, List.of(toText(tooltip)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = click.button() == 0 && super.mouseClicked(click, doubled);
        if (focusedBefore != null && !focusedBefore.equals(focusedField())) controller.commitTextSettings();
        if (handledByField) return true;
        if (click.button() != 0) return false;
        ConfigControlId id = PluginManagerPainter.actionAt(view, (int) click.x(), (int) click.y());
        if (id == null) return false;
        activate(id);
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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
    public boolean keyPressed(KeyInput input) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = super.keyPressed(input);
        if (focusedBefore != null && !focusedBefore.equals(focusedField())) controller.commitTextSettings();
        if (handledByField) return true;
        if (view.dialog() == null
                && (input.key() == GLFW.GLFW_KEY_UP || input.key() == GLFW.GLFW_KEY_DOWN)) {
            controller.commitTextSettings();
            controller.moveSelection(input.key() == GLFW.GLFW_KEY_UP ? -1 : 1);
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
        if (action.type() == ConfigUiAction.Type.CLOSE_TO_PARENT) MinecraftClient.getInstance().setScreen(parent);
        else refresh();
    }

    private void refresh() {
        MinecraftClient.getInstance().setScreen(new PluginManagerScreen(parent, controller));
    }

    @Override
    public void close() {
        controller.commitTextSettings();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private final class Canvas implements PluginManagerPainter.Canvas {
        private final DrawContext context;

        private Canvas(DrawContext context) {
            this.context = context;
        }

        @Override
        public void fill(UiRect bounds, int color) {
            context.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), color);
        }

        @Override
        public void text(UiText text, UiRect bounds, int color, boolean shadow) {
            String value = toText(text).getString();
            if (textRenderer.getWidth(value) > bounds.width()) {
                value = textRenderer.trimToWidth(
                        value, Math.max(0, bounds.width() - textRenderer.getWidth("…"))) + "…";
            }
            context.drawText(textRenderer, value, bounds.x(), bounds.y(), color, shadow);
        }

        @Override
        public void wrapped(UiText text, UiRect bounds, int color) {
            String remaining = toText(text).getString();
            for (int y = bounds.y(); !remaining.isEmpty() && y + 9 <= bounds.y() + bounds.height(); y += 11) {
                String line = textRenderer.trimToWidth(remaining, bounds.width());
                if (line.isEmpty()) break;
                context.drawText(textRenderer, line, bounds.x(), y, color, false);
                remaining = remaining.substring(line.length()).stripLeading();
            }
        }

        @Override
        public void centered(UiText text, int centerX, int y, int color) {
            Text value = toText(text);
            context.drawText(textRenderer, value, centerX - textRenderer.getWidth(value) / 2, y, color, false);
        }

        @Override
        public void pushClip(UiRect bounds) {
            context.enableScissor(bounds.x(), bounds.y(),
                    bounds.x() + bounds.width(), bounds.y() + bounds.height());
        }

        @Override
        public void popClip() {
            context.disableScissor();
        }

        @Override
        public boolean textFieldFocused(ConfigControlId id) {
            TextFieldWidget field = fields.get(id);
            return field != null && field.isFocused();
        }
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
