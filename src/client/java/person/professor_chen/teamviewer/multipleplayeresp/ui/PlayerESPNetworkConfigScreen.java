package person.professor_chen.teamviewer.multipleplayeresp.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import person.professor_chen.teamviewer.multipleplayeresp.core.StandaloneMultiPlayerESP;

public class PlayerESPNetworkConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget updateIntervalField;
    private ButtonWidget uploadEntitiesButton;
    private ButtonWidget uploadSharedWaypointsButton;
    private ButtonWidget useSystemProxyButton;

    private final int originalUpdateInterval;
    private final boolean originalUploadEntities;
    private final boolean originalUploadSharedWaypoints;
    private final boolean originalUseSystemProxy;

    private static final int COMPONENT_WIDTH = 200;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private int startY;
    private int currentY;

    public PlayerESPNetworkConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.network_config.title"));
        this.parent = parent;
        this.originalUpdateInterval = StandaloneMultiPlayerESP.getConfig().getUpdateInterval();
        this.originalUploadEntities = StandaloneMultiPlayerESP.getConfig().isUploadEntities();
        this.originalUploadSharedWaypoints = StandaloneMultiPlayerESP.getConfig().isUploadSharedWaypoints();
        this.originalUseSystemProxy = StandaloneMultiPlayerESP.getConfig().isUseSystemProxy();
    }

    private void calculateLayout() {
        int totalHeight = 0;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
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

    private int getComponentX() {
        return (this.width - COMPONENT_WIDTH) / 2;
    }

    @Override
    protected void init() {
        super.init();

        calculateLayout();
        currentY += COMPONENT_SPACING;

        int componentX = getComponentX();

        int updateIntervalY = getNextY();
        this.updateIntervalField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            updateIntervalY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.update_interval")
        );
        this.updateIntervalField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getUpdateInterval()));
        this.updateIntervalField.setMaxLength(4);
        this.updateIntervalField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.update_interval_hint"));
        this.addDrawableChild(this.updateIntervalField);

        this.addDrawableChild(
            new TextWidget(componentX, updateIntervalY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.update_interval"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int uploadEntitiesY = getNextButtonY();
        this.uploadEntitiesButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.upload_entities"),
            button -> toggleUploadEntities()
        ).dimensions(componentX, uploadEntitiesY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.uploadEntitiesButton);

        int uploadSharedWaypointsY = getNextButtonY();
        this.uploadSharedWaypointsButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.upload_shared_waypoints"),
            button -> toggleUploadSharedWaypoints()
        ).dimensions(componentX, uploadSharedWaypointsY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.uploadSharedWaypointsButton);

        int useSystemProxyY = getNextButtonY();
        this.useSystemProxyButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.use_system_proxy"),
            button -> toggleUseSystemProxy()
        ).dimensions(componentX, useSystemProxyY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.useSystemProxyButton);

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

        updateUploadEntitiesButton();
        updateUploadSharedWaypointsButton();
        updateUseSystemProxyButton();
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
    }

    @Override
    public void close() {
        StandaloneMultiPlayerESP.getConfig().setUpdateInterval(this.originalUpdateInterval);
        StandaloneMultiPlayerESP.getConfig().setUploadEntities(this.originalUploadEntities);
        StandaloneMultiPlayerESP.getConfig().setUploadSharedWaypoints(this.originalUploadSharedWaypoints);
        StandaloneMultiPlayerESP.getConfig().setUseSystemProxy(this.originalUseSystemProxy);
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void saveAndClose() {
        try {
            String updateIntervalStr = this.updateIntervalField.getText().trim();
            if (!updateIntervalStr.isEmpty()) {
                int updateInterval = Integer.parseInt(updateIntervalStr);
                if (updateInterval > 0) {
                    StandaloneMultiPlayerESP.getConfig().setUpdateInterval(updateInterval);
                }
            }
        } catch (NumberFormatException e) {
            // 如果输入格式不正确，忽略错误并使用原始值
        }

        StandaloneMultiPlayerESP.getConfig().save();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void toggleUploadEntities() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isUploadEntities();
        StandaloneMultiPlayerESP.getConfig().setUploadEntities(!currentStatus);
        updateUploadEntitiesButton();
    }

    private void toggleUploadSharedWaypoints() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isUploadSharedWaypoints();
        StandaloneMultiPlayerESP.getConfig().setUploadSharedWaypoints(!currentStatus);
        updateUploadSharedWaypointsButton();
    }

    private void toggleUseSystemProxy() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isUseSystemProxy();
        StandaloneMultiPlayerESP.getConfig().setUseSystemProxy(!currentStatus);
        updateUseSystemProxyButton();
    }

    private void updateUploadEntitiesButton() {
        if (this.uploadEntitiesButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isUploadEntities();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.upload_entities").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.uploadEntitiesButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateUploadSharedWaypointsButton() {
        if (this.uploadSharedWaypointsButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isUploadSharedWaypoints();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.upload_shared_waypoints").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.uploadSharedWaypointsButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateUseSystemProxyButton() {
        if (this.useSystemProxyButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isUseSystemProxy();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.use_system_proxy").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.useSystemProxyButton.setMessage(Text.of(buttonText));
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateUploadEntitiesButton();
        updateUploadSharedWaypointsButton();
        updateUseSystemProxyButton();
    }
}
