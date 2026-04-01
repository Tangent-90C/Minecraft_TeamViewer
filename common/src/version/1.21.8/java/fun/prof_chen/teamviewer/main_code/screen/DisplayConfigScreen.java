package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.core.PlayerProcesses;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class DisplayConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget renderDistanceField;
    private TextFieldWidget tracerTopOffsetField;
    private ButtonWidget tracerStartModeButton;
    private ButtonWidget showBoxesButton;
    private ButtonWidget showLinesButton;
    private ButtonWidget journeyMapRemotePlayerBeaconsButton;
    private ButtonWidget journeyMapRemotePlayerMapMarkersButton;
    private ButtonWidget xrayMarkersAndBoxesButton;
    private ButtonWidget colorSettingsButton;
    private ButtonWidget waypointSettingsButton;

    private static final int COMPONENT_WIDTH = 170;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private static final int COLUMN_GAP = 6;
    private int startY;
    private int currentY;

    public DisplayConfigScreen(Screen parent) {
        super(Text.translatable("screen.mc_teamviewer.display_config.title"));
        this.parent = parent;
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
        this.renderDistanceField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            firstFieldY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.mc_teamviewer.config.render_distance")
        );
        this.renderDistanceField.setText(String.valueOf(PlayerProcesses.getConfig().getRenderDistance()));
        this.renderDistanceField.setMaxLength(100);
        this.renderDistanceField.setPlaceholder(Text.translatable("screen.mc_teamviewer.config.render_distance_hint"));
        this.addDrawableChild(this.renderDistanceField);

        this.addDrawableChild(
            new TextWidget(leftX, firstFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.mc_teamviewer.config.render_distance"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.tracerTopOffsetField = new TextFieldWidget(
            this.textRenderer,
            rightX,
            firstFieldY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.mc_teamviewer.config.tracer_top_offset")
        );
        this.tracerTopOffsetField.setText(String.valueOf(PlayerProcesses.getConfig().getTracerTopOffset()));
        this.tracerTopOffsetField.setMaxLength(10);
        this.tracerTopOffsetField.setPlaceholder(Text.translatable("screen.mc_teamviewer.config.tracer_top_offset_hint"));
        this.addDrawableChild(this.tracerTopOffsetField);

        this.addDrawableChild(
            new TextWidget(rightX, firstFieldY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.mc_teamviewer.config.tracer_top_offset"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int displayToggleY = getNextButtonY();
        this.showBoxesButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.show_boxes"),
            button -> toggleShowBoxes()
        ).dimensions(leftX, displayToggleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showBoxesButton);

        this.showLinesButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.show_tracking_lines"),
            button -> toggleShowLines()
        ).dimensions(rightX, displayToggleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showLinesButton);

        int tracerToggleY = getNextButtonY();
        this.tracerStartModeButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.tracer_start_mode"),
            button -> toggleTracerStartMode()
        ).dimensions(leftX, tracerToggleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.tracerStartModeButton);

        this.xrayMarkersAndBoxesButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.xray_markers_and_boxes"),
            button -> toggleXrayMarkersAndBoxes()
        ).dimensions(rightX, tracerToggleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.xrayMarkersAndBoxesButton);

        int subSettingsY = getNextButtonY();
        this.colorSettingsButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.color_settings"),
            button -> openColorConfig()
        ).dimensions(leftX, subSettingsY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.colorSettingsButton);

        this.waypointSettingsButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.waypoint_settings"),
            button -> openWaypointConfig()
        ).dimensions(rightX, subSettingsY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.waypointSettingsButton);

        int journeyMapToggleY = getNextButtonY();
        this.journeyMapRemotePlayerBeaconsButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.show_journeymap_remote_player_beacons"),
            button -> toggleJourneyMapRemotePlayerBeacons()
        ).dimensions(leftX, journeyMapToggleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.journeyMapRemotePlayerBeaconsButton);

        this.journeyMapRemotePlayerMapMarkersButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.show_journeymap_remote_player_map_markers"),
            button -> toggleJourneyMapRemotePlayerMapMarkers()
        ).dimensions(rightX, journeyMapToggleY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.journeyMapRemotePlayerMapMarkersButton);

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.back"),
            button -> close()
        ).dimensions(leftX, getNextButtonY(), COMPONENT_WIDTH * 2 + COLUMN_GAP, COMPONENT_HEIGHT).build());

        updateTracerStartModeButton();
        updateShowBoxesButton();
        updateShowLinesButton();
        updateJourneyMapRemotePlayerBeaconsButton();
        updateJourneyMapRemotePlayerMapMarkersButton();
        updateXrayMarkersAndBoxesButton();
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
            drawTooltip(context, "screen.mc_teamviewer.config.render_distance.tooltip", mouseX, mouseY);
            return;
        }
        if (this.tracerTopOffsetField != null && this.tracerTopOffsetField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.tracer_top_offset.tooltip", mouseX, mouseY);
            return;
        }
        if (this.showBoxesButton != null && this.showBoxesButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.show_boxes.tooltip", mouseX, mouseY);
            return;
        }
        if (this.showLinesButton != null && this.showLinesButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.show_tracking_lines.tooltip", mouseX, mouseY);
            return;
        }
        if (this.xrayMarkersAndBoxesButton != null && this.xrayMarkersAndBoxesButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.xray_markers_and_boxes.tooltip", mouseX, mouseY);
            return;
        }
        if (this.journeyMapRemotePlayerBeaconsButton != null && this.journeyMapRemotePlayerBeaconsButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.show_journeymap_remote_player_beacons.tooltip", mouseX, mouseY);
            return;
        }
        if (this.journeyMapRemotePlayerMapMarkersButton != null && this.journeyMapRemotePlayerMapMarkersButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.show_journeymap_remote_player_map_markers.tooltip", mouseX, mouseY);
            return;
        }
        if (this.tracerStartModeButton != null && this.tracerStartModeButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.tracer_start_mode.tooltip", mouseX, mouseY);
            return;
        }
        if (this.colorSettingsButton != null && this.colorSettingsButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.color_settings.tooltip", mouseX, mouseY);
            return;
        }
        if (this.waypointSettingsButton != null && this.waypointSettingsButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.mc_teamviewer.config.waypoint_settings.tooltip", mouseX, mouseY);
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
        applyFieldValues();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void openColorConfig() {
        MinecraftClient.getInstance().setScreen(new ColorConfigScreen(this));
    }

    private void openWaypointConfig() {
        MinecraftClient.getInstance().setScreen(new WaypointConfigScreen(this));
    }

    private void toggleTracerStartMode() {
        Config config = PlayerProcesses.getConfig();
        if (config.isTracerStartTop()) {
            config.setTracerStartMode(Config.TRACER_START_CROSSHAIR);
        } else {
            config.setTracerStartMode(Config.TRACER_START_TOP);
        }
        updateTracerStartModeButton();
    }

    private void updateTracerStartModeButton() {
        if (this.tracerStartModeButton != null) {
            Config config = PlayerProcesses.getConfig();
            String modeKey = config.isTracerStartTop()
                ? "screen.mc_teamviewer.config.tracer_start_mode.top"
                : "screen.mc_teamviewer.config.tracer_start_mode.crosshair";
            String buttonText = Text.translatable("screen.mc_teamviewer.config.tracer_start_mode").getString()
                + ": "
                + Text.translatable(modeKey).getString();
            this.tracerStartModeButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleShowBoxes() {
        boolean currentStatus = PlayerProcesses.getConfig().isShowBoxes();
        PlayerProcesses.getConfig().setShowBoxes(!currentStatus);
        updateShowBoxesButton();
    }

    private void toggleShowLines() {
        boolean currentStatus = PlayerProcesses.getConfig().isShowLines();
        PlayerProcesses.getConfig().setShowLines(!currentStatus);
        updateShowLinesButton();
    }

    private void updateShowBoxesButton() {
        if (this.showBoxesButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isShowBoxes();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.show_boxes").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.showBoxesButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateShowLinesButton() {
        if (this.showLinesButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isShowLines();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.show_tracking_lines").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.showLinesButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleJourneyMapRemotePlayerBeacons() {
        boolean currentStatus = PlayerProcesses.getConfig().isShowJourneyMapRemotePlayerBeacons();
        PlayerProcesses.getConfig().setShowJourneyMapRemotePlayerBeacons(!currentStatus);
        updateJourneyMapRemotePlayerBeaconsButton();
    }

    private void updateJourneyMapRemotePlayerBeaconsButton() {
        if (this.journeyMapRemotePlayerBeaconsButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isShowJourneyMapRemotePlayerBeacons();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.show_journeymap_remote_player_beacons").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.journeyMapRemotePlayerBeaconsButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleJourneyMapRemotePlayerMapMarkers() {
        boolean currentStatus = PlayerProcesses.getConfig().isShowJourneyMapRemotePlayerMapMarkers();
        PlayerProcesses.getConfig().setShowJourneyMapRemotePlayerMapMarkers(!currentStatus);
        updateJourneyMapRemotePlayerMapMarkersButton();
    }

    private void updateJourneyMapRemotePlayerMapMarkersButton() {
        if (this.journeyMapRemotePlayerMapMarkersButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isShowJourneyMapRemotePlayerMapMarkers();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.show_journeymap_remote_player_map_markers").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.journeyMapRemotePlayerMapMarkersButton.setMessage(Text.of(buttonText));
        }
    }

    private void toggleXrayMarkersAndBoxes() {
        boolean currentStatus = PlayerProcesses.getConfig().isXrayMarkersAndBoxes();
        PlayerProcesses.getConfig().setXrayMarkersAndBoxes(!currentStatus);
        updateXrayMarkersAndBoxesButton();
    }

    private void updateXrayMarkersAndBoxesButton() {
        if (this.xrayMarkersAndBoxesButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isXrayMarkersAndBoxes();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.xray_markers_and_boxes").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.xrayMarkersAndBoxesButton.setMessage(Text.of(buttonText));
        }
    }

    private void applyFieldValues() {
        try {
            String renderDistanceStr = this.renderDistanceField.getText().trim();
            if (!renderDistanceStr.isEmpty()) {
                int renderDistance = Integer.parseInt(renderDistanceStr);
                if (renderDistance > 0) {
                    PlayerProcesses.getConfig().setRenderDistance(renderDistance);
                }
            }

            String tracerTopOffsetStr = this.tracerTopOffsetField.getText().trim();
            if (!tracerTopOffsetStr.isEmpty()) {
                double tracerTopOffset = Double.parseDouble(tracerTopOffsetStr);
                PlayerProcesses.getConfig().setTracerTopOffset(tracerTopOffset);
            }
        } catch (NumberFormatException e) {
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateTracerStartModeButton();
        updateShowBoxesButton();
        updateShowLinesButton();
        updateJourneyMapRemotePlayerBeaconsButton();
        updateJourneyMapRemotePlayerMapMarkersButton();
        updateXrayMarkersAndBoxesButton();
    }
}
