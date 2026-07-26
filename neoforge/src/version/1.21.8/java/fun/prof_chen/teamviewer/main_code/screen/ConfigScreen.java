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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;

/** Minecraft 1.21.8 NeoForge widget host for the common seven-page configuration model. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private final ConfigUiController session;
    private final ConfigPageId pageId;
    private final Map<ConfigControlId, AbstractWidget> widgets = new HashMap<>();

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
        ConfigPageView page = session.page(pageId, width, height);
        for (ConfigControlView control : page.controls()) addControl(control);
    }

    private void addControl(ConfigControlView control) {
        UiRect bounds = control.bounds();
        AbstractWidget widget;
        if (control.kind() == ConfigControlKind.TEXT_FIELD) {
            EditBox field = new EditBox(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    toComponent(control.label()));
            field.setMaxLength(control.maxLength());
            field.setValue(control.value());
            if (control.hint() != null) field.setHint(toComponent(control.hint()));
            field.setResponder(value -> session.setText(control.id(), value));
            widget = field;
            if (control.labelBounds() != null) {
                UiRect label = control.labelBounds();
                StringWidget labelWidget = new StringWidget(label.x(), label.y(), label.width(), label.height(),
                        toComponent(control.label()), font).alignLeft().setColor(control.color());
                addRenderableWidget(labelWidget);
            }
        } else if (control.kind() == ConfigControlKind.CHECKBOX) {
            Checkbox.Builder builder = Checkbox.builder(toComponent(control.label()), font)
                    .pos(bounds.x(), bounds.y()).maxWidth(bounds.width()).selected(control.checked())
                    .onValueChange((ignored, checked) -> {
                        session.setChecked(control.id(), checked);
                        refreshDynamicControls();
                    });
            if (control.tooltip() != null) builder.tooltip(Tooltip.create(toComponent(control.tooltip())));
            widget = builder.build();
        } else if (control.kind() == ConfigControlKind.BUTTON) {
            Button.Builder builder = Button.builder(styledComponent(control), ignored -> activate(control.id()))
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            if (control.tooltip() != null) builder.tooltip(Tooltip.create(toComponent(control.tooltip())));
            widget = builder.build();
        } else {
            StringWidget label = new StringWidget(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    toComponent(control.label()), font);
            if (control.alignment() == ConfigControlView.TextAlignment.CENTER) label.alignCenter();
            else label.alignLeft();
            label.setColor(control.color());
            widget = label;
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
        for (ConfigControlView control : session.page(pageId, width, height).controls()) {
            AbstractWidget widget = widgets.get(control.id());
            if (widget == null || control.kind() == ConfigControlKind.TEXT_FIELD
                    || control.kind() == ConfigControlKind.CHECKBOX) continue;
            widget.setMessage(styledComponent(control));
            widget.active = control.active();
            widget.visible = control.visible();
            if (widget instanceof StringWidget label) label.setColor(control.color());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        ConfigPageView page = session.page(pageId, width, height);
        graphics.drawCenteredString(font, toComponent(page.title()), width / 2, page.titleY(), 0xFFFFFF);
        for (ConfigControlView control : page.controls()) {
            if (control.tooltip() != null && control.kind() == ConfigControlKind.TEXT
                    && contains(control.bounds(), mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, toComponent(control.tooltip()), mouseX, mouseY);
                break;
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
        if (value == null) return Component.empty();
        MutableComponent result = value.translationKey() == null
                ? Component.literal(value.literal() == null ? "" : value.literal())
                : Component.translatable(value.translationKey(),
                        value.arguments().stream().map(ConfigScreen::toComponent).toArray());
        return value.suffix().isEmpty() ? result : result.append(value.suffix());
    }

    private static Component styledComponent(ConfigControlView control) {
        Component result = toComponent(control.label());
        return control.color() == 0xFFFFFF ? result
                : result.copy().withStyle(style -> style.withColor(control.color()));
    }
}
