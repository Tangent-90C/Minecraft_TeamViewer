package fun.prof_chen.teamviewer.multipleplayeresp.ui;

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

public class PlayerESPWaypointShapeConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget waypointBeamWidthField;
    private TextFieldWidget waypointBeamHeightField;
    private TextFieldWidget tmBeamWidthField;
    private TextFieldWidget tmBeamHeightField;

    private static final int COMPONENT_WIDTH = 170;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private static final int COLUMN_GAP = 6;
    private int startY;
    private int currentY;

    public PlayerESPWaypointShapeConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.waypoint_shape_config.title"));
        this.parent = parent;
    }

    private void calculateLayout() {
        int totalHeight = 0;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
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

        int firstRowY = getNextY();
        this.waypointBeamWidthField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            firstRowY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.waypoint_beacon_beam_width")
        );
        this.waypointBeamWidthField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getWaypointBeaconBeamWidth()));
        this.waypointBeamWidthField.setMaxLength(10);
        this.waypointBeamWidthField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.waypoint_beacon_beam_width_hint"));
        this.addDrawableChild(this.waypointBeamWidthField);

        this.addDrawableChild(
            new TextWidget(leftX, firstRowY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.waypoint_beacon_beam_width"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.waypointBeamHeightField = new TextFieldWidget(
            this.textRenderer,
            rightX,
            firstRowY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.waypoint_beacon_beam_height")
        );
        this.waypointBeamHeightField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getWaypointBeaconBeamHeight()));
        this.waypointBeamHeightField.setMaxLength(10);
        this.waypointBeamHeightField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.waypoint_beacon_beam_height_hint"));
        this.addDrawableChild(this.waypointBeamHeightField);

        this.addDrawableChild(
            new TextWidget(rightX, firstRowY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.waypoint_beacon_beam_height"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int secondRowY = getNextY();
        this.tmBeamWidthField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            secondRowY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.tampermonkey_beam_width")
        );
        this.tmBeamWidthField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getTampermonkeyBeamWidth()));
        this.tmBeamWidthField.setMaxLength(10);
        this.tmBeamWidthField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.tampermonkey_beam_width_hint"));
        this.addDrawableChild(this.tmBeamWidthField);

        this.addDrawableChild(
            new TextWidget(leftX, secondRowY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.tampermonkey_beam_width"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.tmBeamHeightField = new TextFieldWidget(
            this.textRenderer,
            rightX,
            secondRowY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.tampermonkey_beam_height")
        );
        this.tmBeamHeightField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getTampermonkeyBeamHeight()));
        this.tmBeamHeightField.setMaxLength(10);
        this.tmBeamHeightField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.tampermonkey_beam_height_hint"));
        this.addDrawableChild(this.tmBeamHeightField);

        this.addDrawableChild(
            new TextWidget(rightX, secondRowY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.tampermonkey_beam_height"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int backButtonY = getNextButtonY();
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.back"),
            button -> close()
        ).dimensions(leftX, backButtonY, COMPONENT_WIDTH * 2 + COLUMN_GAP, COMPONENT_HEIGHT).build());
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

        if (this.waypointBeamWidthField != null && this.waypointBeamWidthField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.waypoint_beacon_beam_width.tooltip", mouseX, mouseY);
            return;
        }
        if (this.waypointBeamHeightField != null && this.waypointBeamHeightField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.waypoint_beacon_beam_height.tooltip", mouseX, mouseY);
            return;
        }
        if (this.tmBeamWidthField != null && this.tmBeamWidthField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.tampermonkey_beam_width.tooltip", mouseX, mouseY);
            return;
        }
        if (this.tmBeamHeightField != null && this.tmBeamHeightField.isMouseOver(mouseX, mouseY)) {
            drawTooltip(context, "screen.multipleplayeresp.config.tampermonkey_beam_height.tooltip", mouseX, mouseY);
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

    private void applyFieldValues() {
        try {
            String waypointBeamWidthStr = this.waypointBeamWidthField.getText().trim();
            if (!waypointBeamWidthStr.isEmpty()) {
                double waypointBeamWidth = Double.parseDouble(waypointBeamWidthStr);
                StandaloneMultiPlayerESP.getConfig().setWaypointBeaconBeamWidth(waypointBeamWidth);
            }

            String waypointBeamHeightStr = this.waypointBeamHeightField.getText().trim();
            if (!waypointBeamHeightStr.isEmpty()) {
                double waypointBeamHeight = Double.parseDouble(waypointBeamHeightStr);
                StandaloneMultiPlayerESP.getConfig().setWaypointBeaconBeamHeight(waypointBeamHeight);
            }

            String tmBeamWidthStr = this.tmBeamWidthField.getText().trim();
            if (!tmBeamWidthStr.isEmpty()) {
                double tmBeamWidth = Double.parseDouble(tmBeamWidthStr);
                StandaloneMultiPlayerESP.getConfig().setTampermonkeyBeamWidth(tmBeamWidth);
            }

            String tmBeamHeightStr = this.tmBeamHeightField.getText().trim();
            if (!tmBeamHeightStr.isEmpty()) {
                double tmBeamHeight = Double.parseDouble(tmBeamHeightStr);
                StandaloneMultiPlayerESP.getConfig().setTampermonkeyBeamHeight(tmBeamHeight);
            }
        } catch (NumberFormatException e) {
        }
    }
}
