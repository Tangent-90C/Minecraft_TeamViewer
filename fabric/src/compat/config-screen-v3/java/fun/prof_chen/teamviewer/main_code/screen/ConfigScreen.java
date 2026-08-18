package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlKind;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlView;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageId;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigPageView;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiAction;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiController;
import fun.prof_chen.teamviewer.main_code.config.ui.ConfigUiSessions;
import fun.prof_chen.teamviewer.main_code.config.ui.UiRect;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minecraft 1.21.8 widget host for the common configuration UI SDK. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private final ConfigUiController session;
    private final ConfigPageId pageId;
    private final Map<ConfigControlId, ClickableWidget> widgets = new HashMap<>();
    private ConfigPageView pageView;

    public ConfigScreen(Screen parent) {
        this(parent, ConfigUiSessions.create(), ConfigPageId.ROOT);
    }

    public ConfigScreen(Screen parent, ConfigUiController session) {
        this(parent, session, ConfigPageId.ROOT);
    }

    private ConfigScreen(Screen parent, ConfigUiController session, ConfigPageId pageId) {
        super(toText(titleFor(pageId)));
        this.parent = parent;
        this.session = session;
        this.pageId = pageId;
    }

    @Override
    protected void init() {
        super.init();
        widgets.clear();
        pageView = session.page(pageId, width, height);
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
            field.setChangedListener(value -> session.setText(control.id(), value));
            widget = field;
            if (control.labelBounds() != null) {
                UiRect labelBounds = control.labelBounds();
                TextWidget label = new TextWidget(labelBounds.x(), labelBounds.y(), labelBounds.width(), labelBounds.height(),
                        toText(control.label()), textRenderer).alignLeft().setTextColor(control.color());
                addDrawableChild(label);
            }
        } else if (control.kind() == ConfigControlKind.CHECKBOX) {
            CheckboxWidget.Builder builder = CheckboxWidget.builder(toText(control.label()), textRenderer)
                    .pos(bounds.x(), bounds.y())
                    .maxWidth(bounds.width())
                    .checked(control.checked())
                    .callback((ignored, checked) -> {
                        session.setChecked(control.id(), checked);
                        refreshDynamicControls();
                    });
            if (control.tooltip() != null) {
                builder.tooltip(Tooltip.of(toText(control.tooltip())));
            }
            widget = builder.build();
        } else if (control.kind() == ConfigControlKind.BUTTON) {
            ButtonWidget.Builder builder = ButtonWidget.builder(styledText(control), ignored -> activate(control.id()))
                    .dimensions(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            if (control.tooltip() != null) {
                builder.tooltip(Tooltip.of(toText(control.tooltip())));
            }
            widget = builder.build();
        } else {
            TextWidget text = new TextWidget(bounds.x(), bounds.y(), bounds.width(), bounds.height(), toText(control.label()), textRenderer);
            if (control.alignment() == ConfigControlView.TextAlignment.CENTER) {
                text.alignCenter();
            } else {
                text.alignLeft();
            }
            text.setTextColor(control.color());
            widget = text;
        }
        widget.active = control.active();
        widget.visible = control.visible();
        widgets.put(control.id(), widget);
        addDrawableChild(widget);
    }

    private void activate(ConfigControlId id) {
        ConfigUiAction action = session.activate(pageId, id);
        switch (action.type()) {
            case STAY -> refreshDynamicControls();
            case RELOAD_PAGE -> MinecraftClient.getInstance().setScreen(new ConfigScreen(parent, session, pageId));
            case OPEN_PAGE -> MinecraftClient.getInstance().setScreen(action.targetPage() == ConfigPageId.PLUGINS
                    ? new PluginManagerScreen(this, session.pluginManager())
                    : new ConfigScreen(this, session, action.targetPage()));
            case CLOSE_TO_PARENT -> MinecraftClient.getInstance().setScreen(parent);
        }
    }

    @Override
    public void tick() {
        super.tick();
        refreshDynamicControls();
    }

    private void refreshDynamicControls() {
        pageView = session.page(pageId, width, height);
        for (ConfigControlView control : pageView.controls()) {
            ClickableWidget widget = widgets.get(control.id());
            if (widget == null || control.kind() == ConfigControlKind.TEXT_FIELD || control.kind() == ConfigControlKind.CHECKBOX) {
                continue;
            }
            widget.setMessage(styledText(control));
            widget.active = control.active();
            widget.visible = control.visible();
            if (widget instanceof TextWidget text) {
                text.setTextColor(control.color());
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ConfigPageView current = session.page(pageId, width, height);
        context.drawCenteredTextWithShadow(textRenderer, toText(current.title()), width / 2, current.titleY(), 0xFFFFFF);
        for (ConfigControlView control : current.controls()) {
            if (control.tooltip() == null || control.kind() != ConfigControlKind.TEXT || !contains(control.bounds(), mouseX, mouseY)) {
                continue;
            }
            context.drawTooltip(textRenderer, List.of(toText(control.tooltip())), mouseX, mouseY);
            break;
        }
    }

    @Override
    public void close() {
        session.close(pageId);
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static boolean contains(UiRect rect, int x, int y) {
        return x >= rect.x() && x < rect.x() + rect.width() && y >= rect.y() && y < rect.y() + rect.height();
    }

    private static UiText titleFor(ConfigPageId pageId) {
        return UiText.translatable(switch (pageId) {
            case ROOT -> "screen.mc_teamviewer.config.title";
            case DISPLAY -> "screen.mc_teamviewer.display_config.title";
            case NETWORK -> "screen.mc_teamviewer.network_config.title";
            case ENTITY_UPLOAD -> "screen.mc_teamviewer.entity_upload.title";
            case ENTITY_FILTERS -> "screen.mc_teamviewer.entity_upload.filters_title";
            case ENTITY_FILTER_EDIT -> "screen.mc_teamviewer.entity_upload.filter_edit_title";
            case COLOR -> "screen.mc_teamviewer.color_config.title";
            case WAYPOINT -> "screen.mc_teamviewer.waypoint_config.title";
            case WAYPOINT_SHAPE -> "screen.mc_teamviewer.waypoint_shape_config.title";
            case PACKET_CAPTURE -> "screen.mc_teamviewer.packet_capture.title";
            case PLUGINS -> "screen.mc_teamviewer.integration_plugins.title";
            case PLUGIN_DETAIL -> "screen.mc_teamviewer.integration_plugin.title";
            case PLUGIN_RUNTIME_ACTION_CONFIRM -> "screen.mc_teamviewer.integration_plugin.runtime_action_confirm_title";
            case PLUGIN_COPY_GUIDE -> "screen.mc_teamviewer.integration_plugin.copy_guide_title";
            case DISABLED_PLUGINS -> "screen.mc_teamviewer.integration_plugin.disabled_title";
            case DISABLED_PLUGIN_DETAIL -> "screen.mc_teamviewer.integration_plugin.disabled_detail_title";
            case PLUGIN_DELETE_CONFIRM -> "screen.mc_teamviewer.integration_plugin.delete_confirm_title";
        });
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
