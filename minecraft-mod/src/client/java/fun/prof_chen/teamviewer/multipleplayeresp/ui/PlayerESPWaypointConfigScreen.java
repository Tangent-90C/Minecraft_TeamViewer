package fun.prof_chen.teamviewer.multipleplayeresp.ui;

import fun.prof_chen.teamviewer.multipleplayeresp.config.Config;
import fun.prof_chen.teamviewer.multipleplayeresp.core.StandaloneMultiPlayerESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class PlayerESPWaypointConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget waypointTimeoutField;
    private TextFieldWidget longTermWaypointTimeoutField;
    private TextFieldWidget quickMarkMaxCountField;
    private ButtonWidget waypointUiStyleButton;
    private ButtonWidget showSharedWaypointsButton;
    private ButtonWidget showOwnSharedWaypointsOnMinimapButton;
    private ButtonWidget middleDoubleClickMarkButton;
    private ButtonWidget middleClickCancelWaypointButton;
    private ButtonWidget autoCancelWaypointOnEntityDeathButton;
    private ButtonWidget enableLongTermWaypointButton;

    private final int originalWaypointTimeoutSeconds;
    private final int originalLongTermWaypointTimeoutSeconds;
    private final String originalWaypointUiStyle;
    private final boolean originalShowSharedWaypoints;
    private final boolean originalShowOwnSharedWaypointsOnMinimap;
    private final boolean originalEnableMiddleDoubleClickMark;
    private final boolean originalEnableMiddleClickCancelWaypoint;
    private final boolean originalAutoCancelWaypointOnEntityDeath;
    private final boolean originalEnableLongTermWaypoint;
    private final int originalMaxQuickMarkCount;

    private static final int COMPONENT_WIDTH = 170;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private static final int COLUMN_GAP = 6;
    private int startY;
    private int currentY;

    public PlayerESPWaypointConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.waypoint_config.title"));
        this.parent = parent;
        this.originalWaypointTimeoutSeconds = StandaloneMultiPlayerESP.getConfig().getWaypointTimeoutSeconds();
        this.originalLongTermWaypointTimeoutSeconds = StandaloneMultiPlayerESP.getConfig().getLongTermWaypointTimeoutSeconds();
        this.originalWaypointUiStyle = StandaloneMultiPlayerESP.getConfig().getWaypointUiStyle();
        this.originalShowSharedWaypoints = StandaloneMultiPlayerESP.getConfig().isShowSharedWaypoints();
        this.originalShowOwnSharedWaypointsOnMinimap = StandaloneMultiPlayerESP.getConfig().isShowOwnSharedWaypointsOnMinimap();
        this.originalEnableMiddleDoubleClickMark = StandaloneMultiPlayerESP.getConfig().isEnableMiddleDoubleClickMark();
        this.originalEnableMiddleClickCancelWaypoint = StandaloneMultiPlayerESP.getConfig().isEnableMiddleClickCancelWaypoint();
        this.originalAutoCancelWaypointOnEntityDeath = StandaloneMultiPlayerESP.getConfig().isAutoCancelWaypointOnEntityDeath();
        this.originalEnableLongTermWaypoint = StandaloneMultiPlayerESP.getConfig().isEnableLongTermWaypoint();
        this.originalMaxQuickMarkCount = StandaloneMultiPlayerESP.getConfig().getMaxQuickMarkCount();
    }

    private void calculateLayout() {
        int totalHeight = 0;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += BUTTON_SPACING;
        totalHeight += BUTTON_SPACING;
        totalHeight += BUTTON_SPACING;
        totalHeight += BUTTON_SPACING;
        totalHeight += BUTTON_SPACING;

        startY = (this.height - totalHeight) / 2;
        currentY = startY;
    }

    private int getNextY() {
        int result = currentY;
        currentY += COMPONENT_SPACING;
        return result;
    }

    private int getNextButtonY() {
        int result = currentY;
        currentY += BUTTON_SPACING;
        return result;
    }

    private int getLeftColumnX() {
        int totalWidth = COMPONENT_WIDTH * 2 + COLUMN_GAP;
        return (this.width - totalWidth) / 2;
    }

    private int getRightColumnX() {
        return getLeftColumnX() + COMPONENT_WIDTH + COLUMN_GAP;
    }

    @Override
    protected void init() {
        super.init();

        calculateLayout();
        currentY += COMPONENT_SPACING;

        int leftX = getLeftColumnX();
        int rightX = getRightColumnX();

        int firstFieldY = getNextY();
        this.waypointTimeoutField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            firstFieldY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.waypoint_timeout")
        );
        this.waypointTimeoutField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getWaypointTimeoutSeconds()));
        this.waypointTimeoutField.setMaxLength(4);
        this.waypointTimeoutField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.waypoint_timeout_hint"));
        this.addDrawableChild(this.waypointTimeoutField);

        this.addDrawableChild(
            new TextWidget(leftX, firstFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.waypoint_timeout"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.longTermWaypointTimeoutField = new TextFieldWidget(
            this.textRenderer,
            rightX,
            firstFieldY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.long_term_waypoint_timeout")
        );
        this.longTermWaypointTimeoutField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getLongTermWaypointTimeoutSeconds()));
        this.longTermWaypointTimeoutField.setMaxLength(5);
        this.longTermWaypointTimeoutField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.long_term_waypoint_timeout_hint"));
        this.addDrawableChild(this.longTermWaypointTimeoutField);

        this.addDrawableChild(
            new TextWidget(rightX, firstFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.long_term_waypoint_timeout"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int secondFieldY = getNextY();
        this.quickMarkMaxCountField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            secondFieldY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.quick_mark_max_count")
        );
        this.quickMarkMaxCountField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getMaxQuickMarkCount()));
        this.quickMarkMaxCountField.setMaxLength(2);
        this.quickMarkMaxCountField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.quick_mark_max_count_hint"));
        this.addDrawableChild(this.quickMarkMaxCountField);

        this.addDrawableChild(
            new TextWidget(leftX, secondFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.quick_mark_max_count"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.waypointUiStyleButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.waypoint_ui_style"),
            button -> toggleWaypointUiStyle()
        ).dimensions(rightX, secondFieldY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.waypointUiStyleButton);

        int sharedWaypointY = getNextButtonY();
        this.showSharedWaypointsButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.show_shared_waypoints"),
            button -> toggleShowSharedWaypoints()
        ).dimensions(leftX, sharedWaypointY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showSharedWaypointsButton);

        this.showOwnSharedWaypointsOnMinimapButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.show_own_shared_waypoints_on_minimap"),
            button -> toggleShowOwnSharedWaypointsOnMinimap()
        ).dimensions(rightX, sharedWaypointY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showOwnSharedWaypointsOnMinimapButton);

        int middleClickMarkY = getNextButtonY();
        this.middleDoubleClickMarkButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.middle_double_click_mark"),
            button -> toggleMiddleDoubleClickMark()
        ).dimensions(leftX, middleClickMarkY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.middleDoubleClickMarkButton);

        this.middleClickCancelWaypointButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.middle_click_cancel_waypoint"),
            button -> toggleMiddleClickCancelWaypoint()
        ).dimensions(rightX, middleClickMarkY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.middleClickCancelWaypointButton);

        int longTermWaypointY = getNextButtonY();
        this.enableLongTermWaypointButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.enable_long_term_waypoint"),
            button -> toggleEnableLongTermWaypoint()
        ).dimensions(leftX, longTermWaypointY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.enableLongTermWaypointButton);

        this.autoCancelWaypointOnEntityDeathButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.auto_cancel_waypoint_on_entity_death"),
            button -> toggleAutoCancelWaypointOnEntityDeath()
        ).dimensions(rightX, longTermWaypointY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.autoCancelWaypointOnEntityDeathButton);

        int buttonsY = getNextButtonY();
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.done"),
            button -> saveAndClose()
        ).dimensions(leftX, buttonsY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.cancel"),
            button -> close()
        ).dimensions(rightX, buttonsY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build());

        updateShowSharedWaypointsButton();
        updateShowOwnSharedWaypointsOnMinimapButton();
        updateMiddleDoubleClickMarkButton();
        updateMiddleClickCancelWaypointButton();
        updateAutoCancelWaypointOnEntityDeathButton();
        updateEnableLongTermWaypointButton();
        updateWaypointUiStyleButton();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            startY - 30,
            0xFFFFFF
        );

        if (this.waypointTimeoutField != null && this.waypointTimeoutField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.waypoint_timeout.tooltip", mouseX, mouseY);
            return;
        }
        if (this.longTermWaypointTimeoutField != null && this.longTermWaypointTimeoutField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.long_term_waypoint_timeout.tooltip", mouseX, mouseY);
            return;
        }
        if (this.quickMarkMaxCountField != null && this.quickMarkMaxCountField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.quick_mark_max_count.tooltip", mouseX, mouseY);
            return;
        }
        if (this.waypointUiStyleButton != null && this.waypointUiStyleButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.waypoint_ui_style.tooltip", mouseX, mouseY);
            return;
        }
        if (this.showSharedWaypointsButton != null && this.showSharedWaypointsButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.show_shared_waypoints.tooltip", mouseX, mouseY);
            return;
        }
        if (this.showOwnSharedWaypointsOnMinimapButton != null && this.showOwnSharedWaypointsOnMinimapButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.show_own_shared_waypoints_on_minimap.tooltip", mouseX, mouseY);
            return;
        }
        if (this.middleDoubleClickMarkButton != null && this.middleDoubleClickMarkButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.middle_double_click_mark.tooltip", mouseX, mouseY);
            return;
        }
        if (this.middleClickCancelWaypointButton != null && this.middleClickCancelWaypointButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.middle_click_cancel_waypoint.tooltip", mouseX, mouseY);
            return;
        }
        if (this.autoCancelWaypointOnEntityDeathButton != null && this.autoCancelWaypointOnEntityDeathButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.auto_cancel_waypoint_on_entity_death.tooltip", mouseX, mouseY);
            return;
        }
        if (this.enableLongTermWaypointButton != null && this.enableLongTermWaypointButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.enable_long_term_waypoint.tooltip", mouseX, mouseY);
        }
    }

    private void drawTooltip(DrawContext context, String key, int mouseX, int mouseY) {
        String text = Text.translatable(key).getString();
        if (text == null || text.isBlank()) {
            return;
        }
        List<Text> lines = splitTooltipLines(text, 42);
        context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
    }

    private List<Text> splitTooltipLines(String input, int maxChars) {
        List<Text> lines = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return lines;
        }
        String[] words = input.trim().split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
                continue;
            }
            if (currentLine.length() + 1 + word.length() <= maxChars) {
                currentLine.append(' ').append(word);
            } else {
                lines.add(Text.of(currentLine.toString()));
                currentLine.setLength(0);
                currentLine.append(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(Text.of(currentLine.toString()));
        }
        return lines;
    }

    @Override
    public void close() {
        StandaloneMultiPlayerESP.getConfig().setWaypointTimeoutSeconds(this.originalWaypointTimeoutSeconds);
        StandaloneMultiPlayerESP.getConfig().setLongTermWaypointTimeoutSeconds(this.originalLongTermWaypointTimeoutSeconds);
        StandaloneMultiPlayerESP.getConfig().setWaypointUiStyle(this.originalWaypointUiStyle);
        StandaloneMultiPlayerESP.getConfig().setShowSharedWaypoints(this.originalShowSharedWaypoints);
        StandaloneMultiPlayerESP.getConfig().setShowOwnSharedWaypointsOnMinimap(this.originalShowOwnSharedWaypointsOnMinimap);
        StandaloneMultiPlayerESP.getConfig().setEnableMiddleDoubleClickMark(this.originalEnableMiddleDoubleClickMark);
        StandaloneMultiPlayerESP.getConfig().setEnableMiddleClickCancelWaypoint(this.originalEnableMiddleClickCancelWaypoint);
        StandaloneMultiPlayerESP.getConfig().setAutoCancelWaypointOnEntityDeath(this.originalAutoCancelWaypointOnEntityDeath);
        StandaloneMultiPlayerESP.getConfig().setEnableLongTermWaypoint(this.originalEnableLongTermWaypoint);
        StandaloneMultiPlayerESP.getConfig().setMaxQuickMarkCount(this.originalMaxQuickMarkCount);

        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void toggleShowSharedWaypoints() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isShowSharedWaypoints();
        StandaloneMultiPlayerESP.getConfig().setShowSharedWaypoints(!currentStatus);
        updateShowSharedWaypointsButton();
    }

    private void updateShowSharedWaypointsButton() {
        if (this.showSharedWaypointsButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isShowSharedWaypoints();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.show_shared_waypoints").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.showSharedWaypointsButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleShowOwnSharedWaypointsOnMinimap() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isShowOwnSharedWaypointsOnMinimap();
        StandaloneMultiPlayerESP.getConfig().setShowOwnSharedWaypointsOnMinimap(!currentStatus);
        updateShowOwnSharedWaypointsOnMinimapButton();
    }

    private void updateShowOwnSharedWaypointsOnMinimapButton() {
        if (this.showOwnSharedWaypointsOnMinimapButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isShowOwnSharedWaypointsOnMinimap();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.show_own_shared_waypoints_on_minimap").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.showOwnSharedWaypointsOnMinimapButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleMiddleDoubleClickMark() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isEnableMiddleDoubleClickMark();
        StandaloneMultiPlayerESP.getConfig().setEnableMiddleDoubleClickMark(!currentStatus);
        updateMiddleDoubleClickMarkButton();
    }

    private void updateMiddleDoubleClickMarkButton() {
        if (this.middleDoubleClickMarkButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isEnableMiddleDoubleClickMark();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.middle_double_click_mark").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.middleDoubleClickMarkButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleMiddleClickCancelWaypoint() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isEnableMiddleClickCancelWaypoint();
        StandaloneMultiPlayerESP.getConfig().setEnableMiddleClickCancelWaypoint(!currentStatus);
        updateMiddleClickCancelWaypointButton();
    }

    private void updateMiddleClickCancelWaypointButton() {
        if (this.middleClickCancelWaypointButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isEnableMiddleClickCancelWaypoint();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.middle_click_cancel_waypoint").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.middleClickCancelWaypointButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleAutoCancelWaypointOnEntityDeath() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isAutoCancelWaypointOnEntityDeath();
        StandaloneMultiPlayerESP.getConfig().setAutoCancelWaypointOnEntityDeath(!currentStatus);
        updateAutoCancelWaypointOnEntityDeathButton();
    }

    private void updateAutoCancelWaypointOnEntityDeathButton() {
        if (this.autoCancelWaypointOnEntityDeathButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isAutoCancelWaypointOnEntityDeath();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.auto_cancel_waypoint_on_entity_death").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.autoCancelWaypointOnEntityDeathButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleEnableLongTermWaypoint() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isEnableLongTermWaypoint();
        StandaloneMultiPlayerESP.getConfig().setEnableLongTermWaypoint(!currentStatus);
        updateEnableLongTermWaypointButton();
    }

    private void updateEnableLongTermWaypointButton() {
        if (this.enableLongTermWaypointButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isEnableLongTermWaypoint();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.enable_long_term_waypoint").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.enableLongTermWaypointButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleWaypointUiStyle() {
        Config config = StandaloneMultiPlayerESP.getConfig();
        String current = config.getWaypointUiStyle();
        if (Config.WAYPOINT_UI_BEACON.equals(current)) {
            config.setWaypointUiStyle(Config.WAYPOINT_UI_RING);
        } else if (Config.WAYPOINT_UI_RING.equals(current)) {
            config.setWaypointUiStyle(Config.WAYPOINT_UI_PIN);
        } else {
            config.setWaypointUiStyle(Config.WAYPOINT_UI_BEACON);
        }
        updateWaypointUiStyleButton();
    }

    private void updateWaypointUiStyleButton() {
        if (this.waypointUiStyleButton != null) {
            String style = StandaloneMultiPlayerESP.getConfig().getWaypointUiStyle();
            String styleKey;
            if (Config.WAYPOINT_UI_RING.equals(style)) {
                styleKey = "screen.multipleplayeresp.config.waypoint_ui_style.ring";
            } else if (Config.WAYPOINT_UI_PIN.equals(style)) {
                styleKey = "screen.multipleplayeresp.config.waypoint_ui_style.pin";
            } else {
                styleKey = "screen.multipleplayeresp.config.waypoint_ui_style.beacon";
            }
            String buttonText = Text.translatable("screen.multipleplayeresp.config.waypoint_ui_style").getString()
                + ": "
                + Text.translatable(styleKey).getString();
            this.waypointUiStyleButton.setMessage(Text.of(buttonText));
        }
    }

    private void saveAndClose() {
        try {
            String waypointTimeoutStr = this.waypointTimeoutField.getText().trim();
            if (!waypointTimeoutStr.isEmpty()) {
                int waypointTimeout = Integer.parseInt(waypointTimeoutStr);
                StandaloneMultiPlayerESP.getConfig().setWaypointTimeoutSeconds(waypointTimeout);
            }

            String longTermWaypointTimeoutStr = this.longTermWaypointTimeoutField.getText().trim();
            if (!longTermWaypointTimeoutStr.isEmpty()) {
                int longTermWaypointTimeout = Integer.parseInt(longTermWaypointTimeoutStr);
                StandaloneMultiPlayerESP.getConfig().setLongTermWaypointTimeoutSeconds(longTermWaypointTimeout);
            }

            String quickMarkMaxCountStr = this.quickMarkMaxCountField.getText().trim();
            if (!quickMarkMaxCountStr.isEmpty()) {
                int quickMarkMaxCount = Integer.parseInt(quickMarkMaxCountStr);
                StandaloneMultiPlayerESP.getConfig().setMaxQuickMarkCount(quickMarkMaxCount);
            }

            StandaloneMultiPlayerESP.getConfig().save();
        } catch (NumberFormatException e) {
        }

        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public void tick() {
        super.tick();
        updateShowSharedWaypointsButton();
        updateShowOwnSharedWaypointsOnMinimapButton();
        updateMiddleDoubleClickMarkButton();
        updateMiddleClickCancelWaypointButton();
        updateAutoCancelWaypointOnEntityDeathButton();
        updateEnableLongTermWaypointButton();
        updateWaypointUiStyleButton();
    }
}