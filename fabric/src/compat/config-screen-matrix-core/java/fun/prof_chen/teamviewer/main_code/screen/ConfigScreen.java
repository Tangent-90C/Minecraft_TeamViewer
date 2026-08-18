package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlKind;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlView;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageView;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiAction;
import fun.prof_chen.teamviewer.main_code.config.ui.ClientUiSession;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiSessions;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared MatrixStack widget host for Minecraft 1.18-1.19.4. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private final ClientUiSession session;
    private final ConfigPageId pageId;
    private final Map<ConfigControlId, ClickableWidget> widgets = new HashMap<>();
    private ConfigPageView pageView;

    public ConfigScreen(Screen parent) {
        this(parent, ConfigUiSessions.create(), ConfigPageId.ROOT);
    }

    public ConfigScreen(Screen parent, ClientUiSession session) {
        this(parent, session, ConfigPageId.ROOT);
    }

    private ConfigScreen(Screen parent, ClientUiSession session, ConfigPageId pageId) {
        super(toText(titleFor(pageId)));
        this.parent = parent;
        this.session = session;
        this.pageId = pageId;
    }

    @Override
    protected void init() {
        super.init();
        widgets.clear();
        pageView = session.config().page(pageId, width, height);
        for (ConfigControlView control : pageView.controls()) {
            addControl(control);
        }
    }

    private void addControl(ConfigControlView control) {
        UiRect bounds = control.bounds();
        ClickableWidget widget;
        if (control.kind() == ConfigControlKind.TEXT_FIELD) {
            TextFieldWidget field = new TextFieldWidget(textRenderer, bounds.x(), bounds.y(), bounds.width(), bounds.height(), toText(control.label()));
            field.setMaxLength(control.maxLength());
            field.setText(control.value());
            if (control.hint() != null) {
                field.setSuggestion(toText(control.hint()).getString());
            }
            field.setChangedListener(value -> session.config().setText(control.id(), value));
            widget = field;
        } else if (control.kind() == ConfigControlKind.CHECKBOX) {
            CheckboxWidget checkbox = new CheckboxWidget(
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    toText(control.label()), control.checked()) {
                @Override
                public void onPress() {
                    super.onPress();
                    boolean checked = isChecked();
                        session.config().setChecked(control.id(), checked);
                        refreshDynamicControls();
                }
            };
            widget = checkbox;
        } else if (control.kind() == ConfigControlKind.BUTTON) {
            widget = FabricMatrixConfigScreenCompat.button(
                    bounds, styledText(control), () -> activate(control.id()));
        } else {
            return;
        }
        widget.active = control.active();
        widget.visible = control.visible();
        widgets.put(control.id(), widget);
        addDrawableChild(widget);
    }

    private void activate(ConfigControlId id) {
        ConfigUiAction action = session.config().activate(pageId, id);
        switch (action.type()) {
            case STAY -> refreshDynamicControls();
            case RELOAD_PAGE -> MinecraftClient.getInstance().setScreen(new ConfigScreen(parent, session, pageId));
            case OPEN_PAGE -> MinecraftClient.getInstance().setScreen(
                    new ConfigScreen(this, session, action.targetPage()));
            case OPEN_PLUGIN_MANAGER -> MinecraftClient.getInstance().setScreen(
                    new PluginManagerScreen(this, session.plugins()));
            case CLOSE_TO_PARENT -> MinecraftClient.getInstance().setScreen(parent);
        }
    }

    @Override
    public void tick() {
        super.tick();
        refreshDynamicControls();
    }

    private void refreshDynamicControls() {
        pageView = session.config().page(pageId, width, height);
        for (ConfigControlView control : pageView.controls()) {
            ClickableWidget widget = widgets.get(control.id());
            if (widget == null || control.kind() == ConfigControlKind.TEXT_FIELD || control.kind() == ConfigControlKind.CHECKBOX) {
                continue;
            }
            widget.setMessage(styledText(control));
            widget.active = control.active();
            widget.visible = control.visible();
        }
    }

    @Override
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ConfigPageView current = session.config().page(pageId, width, height);
        textRenderer.drawWithShadow(context, toText(current.title()),
                width / 2.0F - textRenderer.getWidth(toText(current.title())) / 2.0F,
                current.titleY(), 0xFFFFFF);
        for (ConfigControlView control : current.controls()) {
            if (control.kind() == ConfigControlKind.TEXT) {
                UiRect bounds = control.bounds();
                Text label = styledText(control);
                float x = control.alignment() == ConfigControlView.TextAlignment.CENTER
                        ? bounds.x() + (bounds.width() - textRenderer.getWidth(label)) / 2.0F
                        : bounds.x();
                textRenderer.draw(context, label, x, bounds.y(), control.color());
            } else if (control.kind() == ConfigControlKind.TEXT_FIELD && control.labelBounds() != null) {
                UiRect labelBounds = control.labelBounds();
                textRenderer.draw(context, toText(control.label()), labelBounds.x(), labelBounds.y(), control.color());
            }
            if (control.tooltip() == null || !control.bounds().contains(mouseX, mouseY)) {
                continue;
            }
            renderTooltip(context, List.of(toText(control.tooltip())), mouseX, mouseY);
            break;
        }
    }

    public void close() {
        session.config().close(pageId);
        MinecraftClient.getInstance().setScreen(parent);
    }

    public void onClose() {
        close();
    }

    private static UiText titleFor(ConfigPageId pageId) {
        return UiText.translatable(pageId.titleKey());
    }

    private static Text toText(UiText value) {
        if (value == null) {
            return FabricMatrixConfigScreenCompat.emptyText();
        }
        MutableText text;
        if (value.translationKey() != null) {
            Object[] arguments = value.arguments().stream().map(ConfigScreen::toText).toArray();
            text = FabricMatrixConfigScreenCompat.translatableText(value.translationKey(), arguments);
        } else {
            text = FabricMatrixConfigScreenCompat.literalText(value.literal() == null ? "" : value.literal());
        }
        if (!value.suffix().isEmpty()) {
            text.append(value.suffix());
        }
        return text;
    }

    private static Text styledText(ConfigControlView control) {
        Text text = toText(control.label());
        return control.color() == 0xFFFFFF ? text : text.copy().styled(style -> style.withColor(control.color()));
    }
}
