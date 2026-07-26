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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;

/** Minecraft 26.1 widget host for the common configuration UI SDK. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private final ConfigUiController session;
    private final ConfigPageId pageId;
    private final Map<ConfigControlId, AbstractWidget> widgets = new HashMap<>();
    private ConfigPageView pageView;

    public ConfigScreen(Screen parent) {
        this(parent, ConfigUiSessions.create(), ConfigPageId.ROOT);
    }

    public ConfigScreen(Screen parent, ConfigUiController session) {
        this(parent, session, ConfigPageId.ROOT);
    }

    private ConfigScreen(Screen parent, ConfigUiController session, ConfigPageId pageId) {
        super(toComponent(titleFor(pageId)));
        this.parent = parent;
        this.session = session;
        this.pageId = pageId;
    }

    @Override
    protected void init() {
        widgets.clear();
        pageView = session.page(pageId, width, height);
        for (ConfigControlView control : pageView.controls()) {
            addControl(control);
        }
    }

    private void addControl(ConfigControlView control) {
        if (control.kind() == ConfigControlKind.TEXT) {
            return;
        }
        UiRect bounds = control.bounds();
        AbstractWidget widget;
        if (control.kind() == ConfigControlKind.TEXT_FIELD) {
            EditBox field = new EditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(), toComponent(control.label()));
            field.setMaxLength(control.maxLength());
            field.setValue(control.value());
            if (control.hint() != null) {
                field.setHint(toComponent(control.hint()));
            }
            field.setResponder(value -> session.setText(control.id(), value));
            widget = field;
        } else if (control.kind() == ConfigControlKind.CHECKBOX) {
            Checkbox.Builder builder = Checkbox.builder(toComponent(control.label()), font)
                    .pos(bounds.x(), bounds.y())
                    .maxWidth(bounds.width())
                    .selected(control.checked())
                    .onValueChange((ignored, checked) -> {
                        session.setChecked(control.id(), checked);
                        refreshDynamicControls();
                    });
            if (control.tooltip() != null) {
                builder.tooltip(Tooltip.create(toComponent(control.tooltip())));
            }
            widget = builder.build();
        } else {
            Button.Builder builder = Button.builder(styledComponent(control), ignored -> activate(control.id()))
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            if (control.tooltip() != null) {
                builder.tooltip(Tooltip.create(toComponent(control.tooltip())));
            }
            widget = builder.build();
        }
        widget.active = control.active();
        widget.visible = control.visible();
        widgets.put(control.id(), widget);
        addRenderableWidget(widget);
    }

    private void activate(ConfigControlId id) {
        ConfigUiAction action = session.activate(pageId, id);
        switch (action.type()) {
            case STAY -> refreshDynamicControls();
            case RELOAD_PAGE -> minecraft.setScreen(new ConfigScreen(parent, session, pageId));
            case OPEN_PAGE -> minecraft.setScreen(new ConfigScreen(this, session, action.targetPage()));
            case CLOSE_TO_PARENT -> minecraft.setScreen(parent);
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
            AbstractWidget widget = widgets.get(control.id());
            if (widget == null || control.kind() == ConfigControlKind.TEXT_FIELD || control.kind() == ConfigControlKind.CHECKBOX) {
                continue;
            }
            widget.setMessage(styledComponent(control));
            widget.active = control.active();
            widget.visible = control.visible();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        ConfigPageView current = session.page(pageId, width, height);
        graphics.centeredText(font, toComponent(current.title()), width / 2, current.titleY(), 0xFFFFFFFF);
        for (ConfigControlView control : current.controls()) {
            if (control.kind() == ConfigControlKind.TEXT_FIELD && control.labelBounds() != null) {
                UiRect label = control.labelBounds();
                graphics.text(font, toComponent(control.label()), label.x(), label.y(), 0xFFFFFFFF, true);
            } else if (control.kind() == ConfigControlKind.TEXT && control.visible()) {
                UiRect bounds = control.bounds();
                if (control.alignment() == ConfigControlView.TextAlignment.CENTER) {
                    graphics.centeredText(font, toComponent(control.label()), bounds.x() + bounds.width() / 2, bounds.y(), 0xFF000000 | control.color());
                } else {
                    graphics.text(font, toComponent(control.label()), bounds.x(), bounds.y(), 0xFF000000 | control.color(), true);
                }
            }
            if (control.tooltip() != null && control.kind() == ConfigControlKind.TEXT && contains(control.bounds(), mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(toComponent(control.tooltip()), mouseX, mouseY);
            }
        }
    }

    @Override
    public void onClose() {
        session.close(pageId);
        minecraft.setScreen(parent);
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
            case PLUGIN_COPY_GUIDE -> "screen.mc_teamviewer.integration_plugin.copy_guide_title";
            case DISABLED_PLUGINS -> "screen.mc_teamviewer.integration_plugin.disabled_title";
            case DISABLED_PLUGIN_DETAIL -> "screen.mc_teamviewer.integration_plugin.disabled_detail_title";
            case PLUGIN_DELETE_CONFIRM -> "screen.mc_teamviewer.integration_plugin.delete_confirm_title";
        });
    }

    private static Component toComponent(UiText value) {
        if (value == null) {
            return Component.empty();
        }
        MutableComponent component;
        if (value.translationKey() != null) {
            Object[] arguments = value.arguments().stream().map(ConfigScreen::toComponent).toArray();
            component = Component.translatable(value.translationKey(), arguments);
        } else {
            component = Component.literal(value.literal() == null ? "" : value.literal());
        }
        if (!value.suffix().isEmpty()) {
            component.append(value.suffix());
        }
        return component;
    }

    private static Component styledComponent(ConfigControlView control) {
        Component component = toComponent(control.label());
        return control.color() == 0xFFFFFF
                ? component
                : component.copy().withStyle(style -> style.withColor(control.color()));
    }
}
