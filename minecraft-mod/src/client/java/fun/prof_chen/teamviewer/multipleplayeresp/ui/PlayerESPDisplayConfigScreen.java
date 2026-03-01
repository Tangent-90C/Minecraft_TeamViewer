package fun.prof_chen.teamviewer.multipleplayeresp.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import fun.prof_chen.teamviewer.multipleplayeresp.config.Config;
import fun.prof_chen.teamviewer.multipleplayeresp.core.StandaloneMultiPlayerESP;

import java.util.ArrayList;
import java.util.List;

public class PlayerESPDisplayConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget renderDistanceField;
    private TextFieldWidget waypointTimeoutField;
    private TextFieldWidget longTermWaypointTimeoutField;
    private TextFieldWidget quickMarkMaxCountField;
    private TextFieldWidget tracerTopOffsetField;
    private ButtonWidget tracerStartModeButton;
    private ButtonWidget waypointUiStyleButton;
    private ButtonWidget showBoxesButton;
    private ButtonWidget showLinesButton;
    private ButtonWidget showSharedWaypointsButton;
    private ButtonWidget showOwnSharedWaypointsOnMinimapButton;
    private ButtonWidget middleDoubleClickMarkButton;
    private ButtonWidget middleClickCancelWaypointButton;
    private ButtonWidget autoCancelWaypointOnEntityDeathButton;
    private ButtonWidget enableLongTermWaypointButton;
    private ButtonWidget colorSettingsButton;

    private final int originalRenderDistance;
    private final int originalWaypointTimeoutSeconds;
    private final int originalLongTermWaypointTimeoutSeconds;
    private final String originalTracerStartMode;
    private final String originalWaypointUiStyle;
    private final double originalTracerTopOffset;
    private final boolean originalShowBoxes;
    private final boolean originalShowLines;
    private final boolean originalShowSharedWaypoints;
    private final boolean originalShowOwnSharedWaypointsOnMinimap;
    private final boolean originalEnableMiddleDoubleClickMark;
    private final boolean originalEnableMiddleClickCancelWaypoint;
    private final boolean originalAutoCancelWaypointOnEntityDeath;
    private final boolean originalEnableLongTermWaypoint;
    private final int originalMaxQuickMarkCount;
    private final int originalBoxColor;
    private final int originalLineColor;
    private final int originalFriendlyTeamColor;
    private final int originalNeutralTeamColor;
    private final int originalEnemyTeamColor;

    private static final int COMPONENT_WIDTH = 170;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private static final int COLUMN_GAP = 6;
    private int startY;
    private int currentY;

    public PlayerESPDisplayConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.display_config.title"));
        this.parent = parent;
        this.originalRenderDistance = StandaloneMultiPlayerESP.getConfig().getRenderDistance();
        this.originalWaypointTimeoutSeconds = StandaloneMultiPlayerESP.getConfig().getWaypointTimeoutSeconds();
        this.originalLongTermWaypointTimeoutSeconds = StandaloneMultiPlayerESP.getConfig().getLongTermWaypointTimeoutSeconds();
        this.originalTracerStartMode = StandaloneMultiPlayerESP.getConfig().getTracerStartMode();
        this.originalWaypointUiStyle = StandaloneMultiPlayerESP.getConfig().getWaypointUiStyle();
        this.originalTracerTopOffset = StandaloneMultiPlayerESP.getConfig().getTracerTopOffset();
        this.originalShowBoxes = StandaloneMultiPlayerESP.getConfig().isShowBoxes();
        this.originalShowLines = StandaloneMultiPlayerESP.getConfig().isShowLines();
        this.originalShowSharedWaypoints = StandaloneMultiPlayerESP.getConfig().isShowSharedWaypoints();
        this.originalShowOwnSharedWaypointsOnMinimap = StandaloneMultiPlayerESP.getConfig().isShowOwnSharedWaypointsOnMinimap();
        this.originalEnableMiddleDoubleClickMark = StandaloneMultiPlayerESP.getConfig().isEnableMiddleDoubleClickMark();
        this.originalEnableMiddleClickCancelWaypoint = StandaloneMultiPlayerESP.getConfig().isEnableMiddleClickCancelWaypoint();
        this.originalAutoCancelWaypointOnEntityDeath = StandaloneMultiPlayerESP.getConfig().isAutoCancelWaypointOnEntityDeath();
        this.originalEnableLongTermWaypoint = StandaloneMultiPlayerESP.getConfig().isEnableLongTermWaypoint();
        this.originalMaxQuickMarkCount = StandaloneMultiPlayerESP.getConfig().getMaxQuickMarkCount();
        this.originalBoxColor = StandaloneMultiPlayerESP.getConfig().getBoxColor();
        this.originalLineColor = StandaloneMultiPlayerESP.getConfig().getLineColor();
        this.originalFriendlyTeamColor = StandaloneMultiPlayerESP.getConfig().getFriendlyTeamColor();
        this.originalNeutralTeamColor = StandaloneMultiPlayerESP.getConfig().getNeutralTeamColor();
        this.originalEnemyTeamColor = StandaloneMultiPlayerESP.getConfig().getEnemyTeamColor();
    }

    private void calculateLayout() {
        int totalHeight = 0;
        totalHeight += COMPONENT_SPACING; // 顶部留白
        totalHeight += COMPONENT_SPACING; // 第一行输入框
        totalHeight += COMPONENT_SPACING; // 第二行输入框
        totalHeight += BUTTON_SPACING;    // 开关行1
        totalHeight += BUTTON_SPACING;    // 开关行2
        totalHeight += BUTTON_SPACING;    // 开关行3
        totalHeight += BUTTON_SPACING;    // 样式行
        totalHeight += BUTTON_SPACING;    // 颜色设置行
        totalHeight += BUTTON_SPACING;    // 完成/取消

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
        this.renderDistanceField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            firstFieldY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.render_distance")
        );
        this.renderDistanceField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getRenderDistance()));
        this.renderDistanceField.setMaxLength(3);
        this.renderDistanceField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.render_distance_hint"));
        this.addDrawableChild(this.renderDistanceField);

        this.addDrawableChild(
            new TextWidget(leftX, firstFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.render_distance"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.waypointTimeoutField = new TextFieldWidget(
            this.textRenderer,
            rightX,
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
            new TextWidget(rightX, firstFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.waypoint_timeout"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int secondFieldY = getNextY();
        this.longTermWaypointTimeoutField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            secondFieldY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.long_term_waypoint_timeout")
        );
        this.longTermWaypointTimeoutField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getLongTermWaypointTimeoutSeconds()));
        this.longTermWaypointTimeoutField.setMaxLength(5);
        this.longTermWaypointTimeoutField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.long_term_waypoint_timeout_hint"));
        this.addDrawableChild(this.longTermWaypointTimeoutField);

        this.addDrawableChild(
            new TextWidget(leftX, secondFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.long_term_waypoint_timeout"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.tracerTopOffsetField = new TextFieldWidget(
            this.textRenderer,
            rightX,
            secondFieldY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.tracer_top_offset")
        );
        this.tracerTopOffsetField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getTracerTopOffset()));
        this.tracerTopOffsetField.setMaxLength(6);
        this.tracerTopOffsetField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.tracer_top_offset_hint"));
        this.addDrawableChild(this.tracerTopOffsetField);

        this.addDrawableChild(
            new TextWidget(rightX, secondFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.tracer_top_offset"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int displayToggleY = getNextButtonY();
        this.showBoxesButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.show_boxes"),
            button -> toggleShowBoxes()
        ).dimensions(leftX, displayToggleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showBoxesButton);

        this.showLinesButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.show_tracking_lines"),
            button -> toggleShowLines()
        ).dimensions(rightX, displayToggleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showLinesButton);

        int showSharedWaypointsY = getNextButtonY();
        this.showSharedWaypointsButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.show_shared_waypoints"),
            button -> toggleShowSharedWaypoints()
        ).dimensions(leftX, showSharedWaypointsY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showSharedWaypointsButton);

        this.middleDoubleClickMarkButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.middle_double_click_mark"),
            button -> toggleMiddleDoubleClickMark()
        ).dimensions(rightX, showSharedWaypointsY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.middleDoubleClickMarkButton);

        int ownSharedWaypointsY = getNextButtonY();
        this.showOwnSharedWaypointsOnMinimapButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.show_own_shared_waypoints_on_minimap"),
            button -> toggleShowOwnSharedWaypointsOnMinimap()
        ).dimensions(leftX, ownSharedWaypointsY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showOwnSharedWaypointsOnMinimapButton);

        int enableLongTermWaypointY = getNextButtonY();
        this.enableLongTermWaypointButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.enable_long_term_waypoint"),
            button -> toggleEnableLongTermWaypoint()
        ).dimensions(leftX, enableLongTermWaypointY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.enableLongTermWaypointButton);

        this.quickMarkMaxCountField = new TextFieldWidget(
            this.textRenderer,
            rightX,
            enableLongTermWaypointY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.quick_mark_max_count")
        );
        this.quickMarkMaxCountField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getMaxQuickMarkCount()));
        this.quickMarkMaxCountField.setMaxLength(2);
        this.quickMarkMaxCountField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.quick_mark_max_count_hint"));
        this.addDrawableChild(this.quickMarkMaxCountField);

        this.addDrawableChild(
            new TextWidget(rightX, enableLongTermWaypointY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.quick_mark_max_count"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int waypointUiStyleY = getNextButtonY();
        this.waypointUiStyleButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.waypoint_ui_style"),
            button -> toggleWaypointUiStyle()
        ).dimensions(leftX, waypointUiStyleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.waypointUiStyleButton);

        this.tracerStartModeButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.tracer_start_mode"),
            button -> toggleTracerStartMode()
        ).dimensions(rightX, waypointUiStyleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.tracerStartModeButton);

        int colorConfigY = getNextButtonY();
        this.colorSettingsButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.color_settings"),
            button -> openColorConfig()
        ).dimensions(leftX, colorConfigY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.colorSettingsButton);

        this.middleClickCancelWaypointButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.middle_click_cancel_waypoint"),
            button -> toggleMiddleClickCancelWaypoint()
        ).dimensions(rightX, colorConfigY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.middleClickCancelWaypointButton);

        int autoCancelOnDeathY = getNextButtonY();
        this.autoCancelWaypointOnEntityDeathButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.auto_cancel_waypoint_on_entity_death"),
            button -> toggleAutoCancelWaypointOnEntityDeath()
        ).dimensions(leftX, autoCancelOnDeathY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
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

        updateTracerStartModeButton();
        updateShowBoxesButton();
        updateShowLinesButton();
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

        if (this.renderDistanceField != null && this.renderDistanceField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.render_distance.tooltip", mouseX, mouseY);
            return;
        }
        if (this.waypointTimeoutField != null && this.waypointTimeoutField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.waypoint_timeout.tooltip", mouseX, mouseY);
            return;
        }
        if (this.longTermWaypointTimeoutField != null && this.longTermWaypointTimeoutField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.long_term_waypoint_timeout.tooltip", mouseX, mouseY);
            return;
        }
        if (this.tracerTopOffsetField != null && this.tracerTopOffsetField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.tracer_top_offset.tooltip", mouseX, mouseY);
            return;
        }

        if (this.showBoxesButton != null && this.showBoxesButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.show_boxes.tooltip", mouseX, mouseY);
            return;
        }
        if (this.showLinesButton != null && this.showLinesButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.show_tracking_lines.tooltip", mouseX, mouseY);
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
        if (this.tracerStartModeButton != null && this.tracerStartModeButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.tracer_start_mode.tooltip", mouseX, mouseY);
            return;
        }
        if (this.colorSettingsButton != null && this.colorSettingsButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.color_settings.tooltip", mouseX, mouseY);
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
        StandaloneMultiPlayerESP.getConfig().setRenderDistance(this.originalRenderDistance);
        StandaloneMultiPlayerESP.getConfig().setWaypointTimeoutSeconds(this.originalWaypointTimeoutSeconds);
        StandaloneMultiPlayerESP.getConfig().setLongTermWaypointTimeoutSeconds(this.originalLongTermWaypointTimeoutSeconds);
        StandaloneMultiPlayerESP.getConfig().setTracerStartMode(this.originalTracerStartMode);
        StandaloneMultiPlayerESP.getConfig().setWaypointUiStyle(this.originalWaypointUiStyle);
        StandaloneMultiPlayerESP.getConfig().setTracerTopOffset(this.originalTracerTopOffset);
        StandaloneMultiPlayerESP.getConfig().setShowBoxes(this.originalShowBoxes);
        StandaloneMultiPlayerESP.getConfig().setShowLines(this.originalShowLines);
        StandaloneMultiPlayerESP.getConfig().setShowSharedWaypoints(this.originalShowSharedWaypoints);
        StandaloneMultiPlayerESP.getConfig().setShowOwnSharedWaypointsOnMinimap(this.originalShowOwnSharedWaypointsOnMinimap);
        StandaloneMultiPlayerESP.getConfig().setEnableMiddleDoubleClickMark(this.originalEnableMiddleDoubleClickMark);
        StandaloneMultiPlayerESP.getConfig().setEnableMiddleClickCancelWaypoint(this.originalEnableMiddleClickCancelWaypoint);
        StandaloneMultiPlayerESP.getConfig().setAutoCancelWaypointOnEntityDeath(this.originalAutoCancelWaypointOnEntityDeath);
        StandaloneMultiPlayerESP.getConfig().setEnableLongTermWaypoint(this.originalEnableLongTermWaypoint);
        StandaloneMultiPlayerESP.getConfig().setMaxQuickMarkCount(this.originalMaxQuickMarkCount);
        StandaloneMultiPlayerESP.getConfig().setBoxColor(this.originalBoxColor);
        StandaloneMultiPlayerESP.getConfig().setLineColor(this.originalLineColor);
        StandaloneMultiPlayerESP.getConfig().setFriendlyTeamColor(this.originalFriendlyTeamColor);
        StandaloneMultiPlayerESP.getConfig().setNeutralTeamColor(this.originalNeutralTeamColor);
        StandaloneMultiPlayerESP.getConfig().setEnemyTeamColor(this.originalEnemyTeamColor);

        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void openColorConfig() {
        MinecraftClient.getInstance().setScreen(new PlayerESPColorConfigScreen(this));
    }

    private void toggleTracerStartMode() {
        Config config = StandaloneMultiPlayerESP.getConfig();
        if (config.isTracerStartTop()) {
            config.setTracerStartMode(Config.TRACER_START_CROSSHAIR);
        } else {
            config.setTracerStartMode(Config.TRACER_START_TOP);
        }
        updateTracerStartModeButton();
    }

    private void updateTracerStartModeButton() {
        if (this.tracerStartModeButton != null) {
            Config config = StandaloneMultiPlayerESP.getConfig();
            String modeKey = config.isTracerStartTop()
                ? "screen.multipleplayeresp.config.tracer_start_mode.top"
                : "screen.multipleplayeresp.config.tracer_start_mode.crosshair";
            String buttonText = Text.translatable("screen.multipleplayeresp.config.tracer_start_mode").getString()
                + ": "
                + Text.translatable(modeKey).getString();
            this.tracerStartModeButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleShowBoxes() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isShowBoxes();
        StandaloneMultiPlayerESP.getConfig().setShowBoxes(!currentStatus);
        updateShowBoxesButton();
    }

    private void toggleShowLines() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isShowLines();
        StandaloneMultiPlayerESP.getConfig().setShowLines(!currentStatus);
        updateShowLinesButton();
    }

    private void toggleShowSharedWaypoints() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isShowSharedWaypoints();
        StandaloneMultiPlayerESP.getConfig().setShowSharedWaypoints(!currentStatus);
        updateShowSharedWaypointsButton();
    }

    private void updateShowBoxesButton() {
        if (this.showBoxesButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isShowBoxes();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.show_boxes").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.showBoxesButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateShowLinesButton() {
        if (this.showLinesButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isShowLines();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.show_tracking_lines").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.showLinesButton.setMessage(Text.of(buttonText));
        }
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
            String renderDistanceStr = this.renderDistanceField.getText().trim();
            if (!renderDistanceStr.isEmpty()) {
                int renderDistance = Integer.parseInt(renderDistanceStr);
                if (renderDistance > 0) {
                    StandaloneMultiPlayerESP.getConfig().setRenderDistance(renderDistance);
                }
            }

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

            String tracerTopOffsetStr = this.tracerTopOffsetField.getText().trim();
            if (!tracerTopOffsetStr.isEmpty()) {
                double tracerTopOffset = Double.parseDouble(tracerTopOffsetStr);
                StandaloneMultiPlayerESP.getConfig().setTracerTopOffset(tracerTopOffset);
            }

            StandaloneMultiPlayerESP.getConfig().save();
        } catch (NumberFormatException e) {
            // 如果输入格式不正确，忽略错误并使用原始值
        }

        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public void tick() {
        super.tick();
        updateTracerStartModeButton();
        updateShowBoxesButton();
        updateShowLinesButton();
        updateShowSharedWaypointsButton();
        updateShowOwnSharedWaypointsOnMinimapButton();
        updateMiddleDoubleClickMarkButton();
        updateMiddleClickCancelWaypointButton();
        updateAutoCancelWaypointOnEntityDeathButton();
        updateEnableLongTermWaypointButton();
        updateWaypointUiStyleButton();
    }
}
