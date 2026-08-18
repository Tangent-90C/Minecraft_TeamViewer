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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared DrawContext widget host; compile-time shims own native API differences. */
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
                field.setPlaceholder(toText(control.hint()));
            }
            field.setChangedListener(value -> session.config().setText(control.id(), value));
            widget = field;
            if (control.labelBounds() != null) {
                UiRect labelBounds = control.labelBounds();
                addDrawableChild(FabricConfigScreenCompat.label(
                        textRenderer, labelBounds, toText(control.label()), control.color(),
                        ConfigControlView.TextAlignment.LEFT));
            }
        } else if (control.kind() == ConfigControlKind.CHECKBOX) {
            widget = FabricConfigScreenCompat.checkbox(
                    textRenderer, bounds, toText(control.label()), control.checked(),
                    control.tooltip() == null ? null : toText(control.tooltip()), checked -> {
                        session.config().setChecked(control.id(), checked);
                        refreshDynamicControls();
                    });
        } else if (control.kind() == ConfigControlKind.BUTTON) {
            ButtonWidget.Builder builder = ButtonWidget.builder(styledText(control), ignored -> activate(control.id()))
                    .dimensions(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            if (control.tooltip() != null) {
                builder.tooltip(Tooltip.of(toText(control.tooltip())));
            }
            widget = builder.build();
        } else {
            widget = FabricConfigScreenCompat.label(
                    textRenderer, bounds, toText(control.label()), control.color(), control.alignment());
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
            FabricConfigScreenCompat.refreshLabel(widget, styledText(control), control.color());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ConfigPageView current = session.config().page(pageId, width, height);
        context.drawCenteredTextWithShadow(textRenderer, toText(current.title()), width / 2, current.titleY(), 0xFFFFFF);
        for (ConfigControlView control : current.controls()) {
            if (control.tooltip() == null || control.kind() != ConfigControlKind.TEXT
                    || !control.bounds().contains(mouseX, mouseY)) {
                continue;
            }
            context.drawTooltip(textRenderer, List.of(toText(control.tooltip())), mouseX, mouseY);
            break;
        }
    }

    @Override
    public void close() {
        session.config().close(pageId);
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static UiText titleFor(ConfigPageId pageId) {
        return UiText.translatable(pageId.titleKey());
    }

    private static Text toText(UiText value) {
        if (value == null) {
            return Text.empty();
        }
        MutableText text;
        if (value.translationKey() != null) {
            Object[] arguments = value.arguments().stream().map(ConfigScreen::toText).toArray();
            text = Text.translatable(value.translationKey(), arguments);
        } else {
            text = Text.literal(value.literal() == null ? "" : value.literal());
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
