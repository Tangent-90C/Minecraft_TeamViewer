package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapMode;
import fun.prof_chen.teamviewer.main_code.core.PlayerProcesses;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

public class NetworkConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget updateIntervalField;
    private TextFieldWidget battleMapUpdateIntervalField;
    private TextFieldWidget battleMapKeepaliveField;
    private TextFieldWidget battleMapCacheRetentionField;
    private ButtonWidget uploadEntitiesButton;
    private ButtonWidget uploadSharedWaypointsButton;
    private ButtonWidget preferLocalDataForRenderButton;
    private ButtonWidget useSystemProxyButton;
    private ButtonWidget battleMapSyncButton;
    private ButtonWidget battleMapModeButton;
    private ButtonWidget battleMapDebugButton;
    private ButtonWidget packetCapturePageButton;

    private static final int MAX_COMPONENT_WIDTH = 200;
    private static final int MIN_COMPONENT_WIDTH = 150;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private static final int COLUMN_GAP = 8;
    private static final int SCREEN_HORIZONTAL_MARGIN = 40;
    private int startY;
    private int currentY;

    public NetworkConfigScreen(Screen parent) {
        super(Text.translatable("screen.mc_teamviewer.network_config.title"));
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

    private int getColumnWidth() {
        int availableWidth = Math.max(
            MIN_COMPONENT_WIDTH * 2 + COLUMN_GAP,
            this.width - SCREEN_HORIZONTAL_MARGIN
        );
        return Math.max(
            MIN_COMPONENT_WIDTH,
            Math.min(MAX_COMPONENT_WIDTH, (availableWidth - COLUMN_GAP) / 2)
        );
    }

    private int getLeftColumnX() {
        int totalWidth = getColumnWidth() * 2 + COLUMN_GAP;
        return (this.width - totalWidth) / 2;
    }

    private int getRightColumnX() {
        return getLeftColumnX() + getColumnWidth() + COLUMN_GAP;
    }

    @Override
    protected void init() {
        super.init();

        calculateLayout();
        currentY += COMPONENT_SPACING;

        int leftX = getLeftColumnX();
        int rightX = getRightColumnX();
        int componentWidth = getColumnWidth();

        int intervalFieldsY = getNextY();
        this.updateIntervalField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            intervalFieldsY,
            componentWidth,
            COMPONENT_HEIGHT,
            Text.translatable("screen.mc_teamviewer.config.update_interval")
        );
        this.updateIntervalField.setText(String.valueOf(PlayerProcesses.getConfig().getUpdateInterval()));
        this.updateIntervalField.setMaxLength(5);
        this.updateIntervalField.setPlaceholder(Text.translatable("screen.mc_teamviewer.config.update_interval_hint"));
        this.addDrawableChild(this.updateIntervalField);

        this.addDrawableChild(
            new TextWidget(leftX, intervalFieldsY - LABEL_SPACING, componentWidth, 12,
                Text.translatable("screen.mc_teamviewer.config.update_interval"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.battleMapUpdateIntervalField = new TextFieldWidget(
            this.textRenderer,
            rightX,
            intervalFieldsY,
            componentWidth,
            COMPONENT_HEIGHT,
            Text.translatable("screen.mc_teamviewer.config.battle_map_update_interval")
        );
        this.battleMapUpdateIntervalField.setText(String.valueOf(PlayerProcesses.getConfig().getBattleMapUpdateIntervalTicks()));
        this.battleMapUpdateIntervalField.setMaxLength(5);
        this.battleMapUpdateIntervalField.setPlaceholder(Text.translatable("screen.mc_teamviewer.config.battle_map_update_interval_hint"));
        this.addDrawableChild(this.battleMapUpdateIntervalField);
        this.addDrawableChild(
            new TextWidget(rightX, intervalFieldsY - LABEL_SPACING, componentWidth, 12,
                Text.translatable("screen.mc_teamviewer.config.battle_map_update_interval"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int battleMapFieldsY = getNextY();
        this.battleMapKeepaliveField = new TextFieldWidget(
            this.textRenderer,
            leftX,
            battleMapFieldsY,
            componentWidth,
            COMPONENT_HEIGHT,
            Text.translatable("screen.mc_teamviewer.config.battle_map_keepalive_interval")
        );
        this.battleMapKeepaliveField.setText(String.valueOf(PlayerProcesses.getConfig().getBattleMapKeepaliveIntervalSeconds()));
        this.battleMapKeepaliveField.setMaxLength(5);
        this.battleMapKeepaliveField.setPlaceholder(Text.translatable("screen.mc_teamviewer.config.battle_map_keepalive_interval_hint"));
        this.addDrawableChild(this.battleMapKeepaliveField);
        this.addDrawableChild(
            new TextWidget(leftX, battleMapFieldsY - LABEL_SPACING, componentWidth, 12,
                Text.translatable("screen.mc_teamviewer.config.battle_map_keepalive_interval"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        this.battleMapCacheRetentionField = new TextFieldWidget(
            this.textRenderer,
            rightX,
            battleMapFieldsY,
            componentWidth,
            COMPONENT_HEIGHT,
            Text.translatable("screen.mc_teamviewer.config.battle_map_cache_retention")
        );
        this.battleMapCacheRetentionField.setText(String.valueOf(PlayerProcesses.getConfig().getBattleMapCacheRetentionSeconds()));
        this.battleMapCacheRetentionField.setMaxLength(6);
        this.battleMapCacheRetentionField.setPlaceholder(Text.translatable("screen.mc_teamviewer.config.battle_map_cache_retention_hint"));
        this.addDrawableChild(this.battleMapCacheRetentionField);
        this.addDrawableChild(
            new TextWidget(rightX, battleMapFieldsY - LABEL_SPACING, componentWidth, 12,
                Text.translatable("screen.mc_teamviewer.config.battle_map_cache_retention"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );

        int uploadToggleY = getNextButtonY();
        this.uploadEntitiesButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.upload_entities"),
            button -> toggleUploadEntities()
        ).dimensions(leftX, uploadToggleY, componentWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.uploadEntitiesButton);

        this.uploadSharedWaypointsButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.upload_shared_waypoints"),
            button -> toggleUploadSharedWaypoints()
        ).dimensions(rightX, uploadToggleY, componentWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.uploadSharedWaypointsButton);

        int renderToggleY = getNextButtonY();
        this.preferLocalDataForRenderButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.prefer_local_data_for_rendering"),
            button -> togglePreferLocalDataForRender()
        ).dimensions(leftX, renderToggleY, componentWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.preferLocalDataForRenderButton);

        this.useSystemProxyButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.use_system_proxy"),
            button -> toggleUseSystemProxy()
        ).dimensions(rightX, renderToggleY, componentWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.useSystemProxyButton);

        int battleMapToggleY = getNextButtonY();
        this.battleMapSyncButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.battle_map_sync"),
            button -> toggleBattleMapSync()
        ).dimensions(leftX, battleMapToggleY, componentWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.battleMapSyncButton);

        this.battleMapModeButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.battle_map_mode"),
            button -> cycleBattleMapMode()
        ).dimensions(rightX, battleMapToggleY, componentWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.battleMapModeButton);

        int footerY = getNextButtonY();
        this.battleMapDebugButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.battle_map_debug"),
            button -> toggleBattleMapDebug()
        ).dimensions(leftX, footerY, componentWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.battleMapDebugButton);

        this.packetCapturePageButton = ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.packet_capture_page"),
            button -> openPacketCapturePage()
        ).dimensions(rightX, footerY, componentWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.packetCapturePageButton);

        int backButtonY = getNextButtonY();
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.mc_teamviewer.config.back"),
            button -> close()
        ).dimensions(leftX, backButtonY, componentWidth * 2 + COLUMN_GAP, COMPONENT_HEIGHT).build());

        updateUploadEntitiesButton();
        updateUploadSharedWaypointsButton();
        updatePreferLocalDataForRenderButton();
        updateUseSystemProxyButton();
        updateBattleMapSyncButton();
        updateBattleMapModeButton();
        updateBattleMapDebugButton();
        updatePacketCapturePageButton();
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
        applyFieldValues();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void applyFieldValues() {
        try {
            String updateIntervalStr = this.updateIntervalField.getText().trim();
            if (!updateIntervalStr.isEmpty()) {
                int updateInterval = Integer.parseInt(updateIntervalStr);
                if (updateInterval > 0) {
                    PlayerProcesses.getConfig().setUpdateInterval(updateInterval);
                }
            }
            String battleMapUpdateIntervalStr = this.battleMapUpdateIntervalField.getText().trim();
            if (!battleMapUpdateIntervalStr.isEmpty()) {
                int battleMapUpdateInterval = Integer.parseInt(battleMapUpdateIntervalStr);
                if (battleMapUpdateInterval > 0) {
                    PlayerProcesses.getConfig().setBattleMapUpdateIntervalTicks(battleMapUpdateInterval);
                }
            }
            String battleMapKeepaliveStr = this.battleMapKeepaliveField.getText().trim();
            if (!battleMapKeepaliveStr.isEmpty()) {
                int battleMapKeepalive = Integer.parseInt(battleMapKeepaliveStr);
                if (battleMapKeepalive > 0) {
                    PlayerProcesses.getConfig().setBattleMapKeepaliveIntervalSeconds(battleMapKeepalive);
                }
            }
            String battleMapCacheRetentionStr = this.battleMapCacheRetentionField.getText().trim();
            if (!battleMapCacheRetentionStr.isEmpty()) {
                int battleMapCacheRetention = Integer.parseInt(battleMapCacheRetentionStr);
                if (battleMapCacheRetention > 0) {
                    PlayerProcesses.getConfig().setBattleMapCacheRetentionSeconds(battleMapCacheRetention);
                }
            }
        } catch (NumberFormatException e) {
            // 如果输入格式不正确，忽略错误并使用原始值
        }
    }

    private void toggleUploadEntities() {
        boolean currentStatus = PlayerProcesses.getConfig().isUploadEntities();
        PlayerProcesses.getConfig().setUploadEntities(!currentStatus);
        updateUploadEntitiesButton();
    }

    private void toggleUploadSharedWaypoints() {
        boolean currentStatus = PlayerProcesses.getConfig().isUploadSharedWaypoints();
        PlayerProcesses.getConfig().setUploadSharedWaypoints(!currentStatus);
        updateUploadSharedWaypointsButton();
    }

    private void toggleUseSystemProxy() {
        boolean currentStatus = PlayerProcesses.getConfig().isUseSystemProxy();
        PlayerProcesses.getConfig().setUseSystemProxy(!currentStatus);
        updateUseSystemProxyButton();
    }

    private void togglePreferLocalDataForRender() {
        boolean currentStatus = PlayerProcesses.getConfig().isPreferLocalDataForRender();
        PlayerProcesses.getConfig().setPreferLocalDataForRender(!currentStatus);
        updatePreferLocalDataForRenderButton();
    }

    private void toggleBattleMapSync() {
        boolean currentStatus = PlayerProcesses.getConfig().isBattleMapSyncEnabled();
        PlayerProcesses.getConfig().setBattleMapSyncEnabled(!currentStatus);
        updateBattleMapSyncButton();
    }

    private void cycleBattleMapMode() {
        BattleMapMode currentMode = BattleMapMode.fromId(PlayerProcesses.getConfig().getBattleMapMode());
        PlayerProcesses.getConfig().setBattleMapMode(currentMode.next().id());
        updateBattleMapModeButton();
    }

    private void toggleBattleMapDebug() {
        boolean currentStatus = PlayerProcesses.getConfig().isBattleMapDebugEnabled();
        PlayerProcesses.getConfig().setBattleMapDebugEnabled(!currentStatus);
        updateBattleMapDebugButton();
    }

    private void openPacketCapturePage() {
        MinecraftClient.getInstance().setScreen(new PacketCaptureScreen(this));
    }

    private void updateUploadEntitiesButton() {
        if (this.uploadEntitiesButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isUploadEntities();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.upload_entities").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.uploadEntitiesButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateUploadSharedWaypointsButton() {
        if (this.uploadSharedWaypointsButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isUploadSharedWaypoints();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.upload_shared_waypoints").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.uploadSharedWaypointsButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateUseSystemProxyButton() {
        if (this.useSystemProxyButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isUseSystemProxy();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.use_system_proxy").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.useSystemProxyButton.setMessage(Text.of(buttonText));
        }
    }

    private void updatePreferLocalDataForRenderButton() {
        if (this.preferLocalDataForRenderButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isPreferLocalDataForRender();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.prefer_local_data_for_rendering").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.preferLocalDataForRenderButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateBattleMapSyncButton() {
        if (this.battleMapSyncButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isBattleMapSyncEnabled();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.battle_map_sync").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.battleMapSyncButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateBattleMapModeButton() {
        if (this.battleMapModeButton != null) {
            BattleMapMode mode = BattleMapMode.fromId(PlayerProcesses.getConfig().getBattleMapMode());
            String buttonText = Text.translatable("screen.mc_teamviewer.config.battle_map_mode").getString();
            buttonText += " [" + Text.translatable(mode.translationKey()).getString() + "]";
            this.battleMapModeButton.setMessage(Text.of(buttonText));
        }
    }

    private void updateBattleMapDebugButton() {
        if (this.battleMapDebugButton != null) {
            boolean isEnabled = PlayerProcesses.getConfig().isBattleMapDebugEnabled();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.battle_map_debug").getString();
            buttonText += isEnabled ? " [ON]" : " [OFF]";
            this.battleMapDebugButton.setMessage(Text.of(buttonText));
        }
    }

    private void updatePacketCapturePageButton() {
        if (this.packetCapturePageButton != null) {
            boolean isCapturing = PlayerProcesses.getNetworkManager().isPacketDumpCaptureActive();
            String buttonText = Text.translatable("screen.mc_teamviewer.config.packet_capture_page").getString();
            buttonText += isCapturing ? " [RUN]" : " [IDLE]";
            this.packetCapturePageButton.setMessage(Text.of(buttonText));
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateUploadEntitiesButton();
        updateUploadSharedWaypointsButton();
        updatePreferLocalDataForRenderButton();
        updateUseSystemProxyButton();
        updateBattleMapSyncButton();
        updateBattleMapModeButton();
        updateBattleMapDebugButton();
        updatePacketCapturePageButton();
    }
}
