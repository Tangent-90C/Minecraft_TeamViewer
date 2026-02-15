package person.professor_chen.teamviewer.multipleplayeresp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

public class PlayerESPDisplayConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget renderDistanceField;
    private TextFieldWidget tracerTopOffsetField;
    private ButtonWidget tracerStartModeButton;

    private final int originalRenderDistance;
    private final String originalTracerStartMode;
    private final double originalTracerTopOffset;
    private final int originalBoxColor;
    private final int originalLineColor;

    private static final int COMPONENT_WIDTH = 200;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private int startY;
    private int currentY;

    public PlayerESPDisplayConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.display_config.title"));
        this.parent = parent;
        this.originalRenderDistance = StandaloneMultiPlayerESP.getConfig().getRenderDistance();
        this.originalTracerStartMode = StandaloneMultiPlayerESP.getConfig().getTracerStartMode();
        this.originalTracerTopOffset = StandaloneMultiPlayerESP.getConfig().getTracerTopOffset();
        this.originalBoxColor = StandaloneMultiPlayerESP.getConfig().getBoxColor();
        this.originalLineColor = StandaloneMultiPlayerESP.getConfig().getLineColor();
    }

    private void calculateLayout() {
        int totalHeight = 0;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
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

    private int getComponentX() {
        return (this.width - COMPONENT_WIDTH) / 2;
    }

    @Override
    protected void init() {
        super.init();

        calculateLayout();
        currentY += COMPONENT_SPACING;

        int componentX = getComponentX();

        int renderDistanceY = getNextY();
        this.renderDistanceField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            renderDistanceY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.render_distance")
        );
        this.renderDistanceField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getRenderDistance()));
        this.renderDistanceField.setMaxLength(3);
        this.renderDistanceField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.render_distance_hint"));
        this.addDrawableChild(this.renderDistanceField);

        this.addDrawableChild(
            new TextWidget(componentX, renderDistanceY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.render_distance"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int tracerTopOffsetY = getNextY();
        this.tracerTopOffsetField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            tracerTopOffsetY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.tracer_top_offset")
        );
        this.tracerTopOffsetField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getTracerTopOffset()));
        this.tracerTopOffsetField.setMaxLength(6);
        this.tracerTopOffsetField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.tracer_top_offset_hint"));
        this.addDrawableChild(this.tracerTopOffsetField);

        this.addDrawableChild(
            new TextWidget(componentX, tracerTopOffsetY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.tracer_top_offset"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int tracerStartModeY = getNextButtonY();
        this.tracerStartModeButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.tracer_start_mode"),
            button -> toggleTracerStartMode()
        ).dimensions(componentX, tracerStartModeY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.tracerStartModeButton);

        int colorConfigY = getNextButtonY();
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.color_settings"),
            button -> openColorConfig()
        ).dimensions(componentX, colorConfigY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build());

        int buttonsY = getNextButtonY();
        int buttonWidth = (COMPONENT_WIDTH - 2) / 2;
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.done"),
            button -> saveAndClose()
        ).dimensions(componentX, buttonsY, buttonWidth, COMPONENT_HEIGHT).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.cancel"),
            button -> close()
        ).dimensions(componentX + buttonWidth + 2, buttonsY, buttonWidth, COMPONENT_HEIGHT).build());

        updateTracerStartModeButton();
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
            context.drawTooltip(
                this.textRenderer,
                Text.translatable("screen.multipleplayeresp.config.render_distance.tooltip"),
                mouseX,
                mouseY
            );
        }
    }

    @Override
    public void close() {
        StandaloneMultiPlayerESP.getConfig().setRenderDistance(this.originalRenderDistance);
        StandaloneMultiPlayerESP.getConfig().setTracerStartMode(this.originalTracerStartMode);
        StandaloneMultiPlayerESP.getConfig().setTracerTopOffset(this.originalTracerTopOffset);
        StandaloneMultiPlayerESP.getConfig().setBoxColor(this.originalBoxColor);
        StandaloneMultiPlayerESP.getConfig().setLineColor(this.originalLineColor);

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

    private void saveAndClose() {
        try {
            String renderDistanceStr = this.renderDistanceField.getText().trim();
            if (!renderDistanceStr.isEmpty()) {
                int renderDistance = Integer.parseInt(renderDistanceStr);
                if (renderDistance > 0) {
                    StandaloneMultiPlayerESP.getConfig().setRenderDistance(renderDistance);
                }
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
    }
}
