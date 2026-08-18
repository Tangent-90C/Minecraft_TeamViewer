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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;

/** Shared NeoForge widget host; compile-time shims own native API differences. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private final ClientUiSession session;
    private final ConfigPageId pageId;
    private final Map<ConfigControlId, AbstractWidget> widgets = new HashMap<>();

    public ConfigScreen(Screen parent) {
        this(parent, ConfigUiSessions.create(), ConfigPageId.ROOT);
    }

    public ConfigScreen(Screen parent, ClientUiSession session) {
        this(parent, session, ConfigPageId.ROOT);
    }

    private ConfigScreen(Screen parent, ClientUiSession session, ConfigPageId pageId) {
        super(toComponent(titleFor(pageId)));
        this.parent = parent;
        this.session = session;
        this.pageId = pageId;
    }

    @Override
    protected void init() {
        widgets.clear();
        ConfigPageView page = session.config().page(pageId, width, height);
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
            field.setResponder(value -> session.config().setText(control.id(), value));
            widget = field;
            if (control.labelBounds() != null) {
                UiRect label = control.labelBounds();
                addRenderableWidget(NeoForgeConfigScreenCompat.label(
                        font, label, toComponent(control.label()), control.color(),
                        ConfigControlView.TextAlignment.LEFT));
            }
        } else if (control.kind() == ConfigControlKind.CHECKBOX) {
            widget = NeoForgeConfigScreenCompat.checkbox(
                    font, bounds, toComponent(control.label()), control.checked(),
                    control.tooltip() == null ? null : toComponent(control.tooltip()), checked -> {
                        session.config().setChecked(control.id(), checked);
                        refreshDynamicControls();
                    });
        } else if (control.kind() == ConfigControlKind.BUTTON) {
            Button.Builder builder = Button.builder(styledComponent(control), ignored -> activate(control.id()))
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            if (control.tooltip() != null) builder.tooltip(Tooltip.create(toComponent(control.tooltip())));
            widget = builder.build();
        } else {
            widget = NeoForgeConfigScreenCompat.label(
                    font, bounds, toComponent(control.label()), control.color(), control.alignment());
        }
        widget.active = control.active();
        widget.visible = control.visible();
        widgets.put(control.id(), widget);
        addRenderableWidget(widget);
    }

    private void activate(ConfigControlId id) {
        ConfigUiAction action = session.config().activate(pageId, id);
        switch (action.type()) {
            case STAY -> refreshDynamicControls();
            case RELOAD_PAGE -> minecraft.setScreen(new ConfigScreen(parent, session, pageId));
            case OPEN_PAGE -> minecraft.setScreen(
                    new ConfigScreen(this, session, action.targetPage()));
            case OPEN_PLUGIN_MANAGER -> minecraft.setScreen(
                    new PluginManagerScreen(this, session.plugins()));
            case CLOSE_TO_PARENT -> minecraft.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        super.tick();
        refreshDynamicControls();
    }

    private void refreshDynamicControls() {
        for (ConfigControlView control : session.config().page(pageId, width, height).controls()) {
            AbstractWidget widget = widgets.get(control.id());
            if (widget == null || control.kind() == ConfigControlKind.TEXT_FIELD
                    || control.kind() == ConfigControlKind.CHECKBOX) continue;
            widget.setMessage(styledComponent(control));
            widget.active = control.active();
            widget.visible = control.visible();
            NeoForgeConfigScreenCompat.refreshLabel(widget, control.color());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        ConfigPageView page = session.config().page(pageId, width, height);
        graphics.drawCenteredString(font, toComponent(page.title()), width / 2, page.titleY(), 0xFFFFFF);
        for (ConfigControlView control : page.controls()) {
            if (control.tooltip() != null && control.kind() == ConfigControlKind.TEXT
                    && control.bounds().contains(mouseX, mouseY)) {
                NeoForgeConfigScreenCompat.showTooltip(
                        graphics, font, toComponent(control.tooltip()), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public void onClose() {
        session.config().close(pageId);
        minecraft.setScreen(parent);
    }

    private static UiText titleFor(ConfigPageId pageId) {
        return UiText.translatable(pageId.titleKey());
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
