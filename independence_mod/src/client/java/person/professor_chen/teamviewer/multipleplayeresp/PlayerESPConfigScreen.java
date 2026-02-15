package person.professor_chen.teamviewer.multipleplayeresp;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import person.professor_chen.teamviewer.multipleplayeresp.PlayerESPNetworkManager.ConnectionStatusListener;

public class PlayerESPConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget urlField;
    private TextFieldWidget renderDistanceField;
    private TextFieldWidget updateIntervalField;
    private TextFieldWidget tracerTopOffsetField;
    private ButtonWidget connectButton;
    private ButtonWidget colorSettingsButton;
    private ButtonWidget disconnectButton;
    private ButtonWidget showBoxesButton; // 方框开关按钮
    private ButtonWidget showLinesButton; // 追踪线开关按钮
    private ButtonWidget tracerStartModeButton; // 追踪线起点模式按钮
    // 连接状态显示
    private TextWidget connectionStatusWidget;
    private String currentConnectionStatus = "Unknown";
    // 保存原始值，用于取消时恢复
    private final String originalURL;
    private final int originalRenderDistance;
    private final int originalUpdateInterval;
    private final boolean originalShowBoxes;
    private final boolean originalShowLines;
    private final String originalTracerStartMode;
    private final double originalTracerTopOffset;
    
    // 自动布局相关变量
    private static final int COMPONENT_WIDTH = 200;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private int startY;
    private int currentY;
    
    public PlayerESPConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.config.title"));
        this.parent = parent;
        this.originalURL = PlayerESPNetworkManager.getServerURL();
        this.originalRenderDistance = StandaloneMultiPlayerESP.getConfig().getRenderDistance();
        this.originalUpdateInterval = StandaloneMultiPlayerESP.getConfig().getUpdateInterval();
        this.originalShowBoxes = StandaloneMultiPlayerESP.getConfig().isShowBoxes();
        this.originalShowLines = StandaloneMultiPlayerESP.getConfig().isShowLines();
        this.originalTracerStartMode = StandaloneMultiPlayerESP.getConfig().getTracerStartMode();
        this.originalTracerTopOffset = StandaloneMultiPlayerESP.getConfig().getTracerTopOffset();
        // 初始化连接状态
        updateConnectionStatus();
    }
    
    /**
     * 计算起始Y坐标，使所有组件居中显示
     */
    private void calculateLayout() {
        // 计算总高度需求
        int totalHeight = 0;
        totalHeight += COMPONENT_SPACING; // 标题间距
        totalHeight += COMPONENT_SPACING; // URL输入框组
        totalHeight += COMPONENT_SPACING; // 渲染距离输入框组
        totalHeight += COMPONENT_SPACING; // 上报频率输入框组
        totalHeight += BUTTON_SPACING;    // 方框按钮
        totalHeight += BUTTON_SPACING;    // 追踪线按钮
        totalHeight += BUTTON_SPACING;    // 追踪线起点模式按钮
        totalHeight += COMPONENT_SPACING; // 顶部模式偏移输入框组
        totalHeight += BUTTON_SPACING;    // 颜色配置按钮
        totalHeight += BUTTON_SPACING;    // 完成/取消按钮行
        totalHeight += BUTTON_SPACING;    // 连接按钮
        totalHeight += COMPONENT_SPACING; // 连接状态显示
        
        // 计算起始Y坐标
        startY = (this.height - totalHeight) / 2;
        currentY = startY;
    }
    
    /**
     * 获取下一个组件的Y坐标
     */
    private int getNextY() {
        int result = currentY;
        currentY += COMPONENT_SPACING;
        return result;
    }
    
    /**
     * 获取按钮组的Y坐标（较大间距）
     */
    private int getNextButtonY() {
        int result = currentY;
        currentY += BUTTON_SPACING;
        return result;
    }
    
    /**
     * 获取组件的X坐标（居中）
     */
    private int getComponentX() {
        return (this.width - COMPONENT_WIDTH) / 2;
    }
    
    // 连接状态监听器实例
    private final ConnectionStatusListener connectionListener = connected -> MinecraftClient.getInstance().execute(() -> {
        updateConnectionStatus();
        updateConnectionStatusWidget();
    });
    
    @Override
    protected void init() {
        super.init();
        
        // 计算自动布局参数
        calculateLayout();
        
        // 跳过标题间距
        currentY += COMPONENT_SPACING;
        
        // 服务器URL输入框
        int componentX = getComponentX();
        int urlY = getNextY();
        this.urlField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            urlY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.url")
        );
        this.urlField.setText(PlayerESPNetworkManager.getServerURL());
        this.urlField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.url_hint"));
        this.addDrawableChild(this.urlField);
        
        // URL标签
        this.addDrawableChild(
            new TextWidget(componentX, urlY - LABEL_SPACING, COMPONENT_WIDTH, 12, 
                Text.translatable("screen.multipleplayeresp.config.url"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        
        // 渲染距离输入框
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
        this.renderDistanceField.setMaxLength(3); // 渲染距离最大3位数字
        this.renderDistanceField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.render_distance_hint"));
        this.addDrawableChild(this.renderDistanceField);
        
        // 渲染距离标签
        this.addDrawableChild(
            new TextWidget(componentX, renderDistanceY - LABEL_SPACING, COMPONENT_WIDTH, 12, 
                Text.translatable("screen.multipleplayeresp.config.render_distance"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        
        // 上报频率输入框
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
        this.updateIntervalField.setMaxLength(4); // 上报频率最大4位数字
        this.updateIntervalField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.update_interval_hint"));
        this.addDrawableChild(this.updateIntervalField);
        
        // 上报频率标签
        this.addDrawableChild(
            new TextWidget(componentX, updateIntervalY - LABEL_SPACING, COMPONENT_WIDTH, 12, 
                Text.translatable("screen.multipleplayeresp.config.update_interval"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        
        // 方框开关按钮
        int showBoxesY = getNextButtonY();
        this.showBoxesButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.show_boxes"),
            button -> toggleShowBoxes()
        ).dimensions(componentX, showBoxesY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showBoxesButton);

        // 追踪线开关按钮
        int showLinesY = getNextButtonY();
        this.showLinesButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.show_tracking_lines"),
            button -> toggleShowLines()
        ).dimensions(componentX, showLinesY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.showLinesButton);

        // 追踪线起点模式按钮
        int tracerStartModeY = getNextButtonY();
        this.tracerStartModeButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.tracer_start_mode"),
            button -> toggleTracerStartMode()
        ).dimensions(componentX, tracerStartModeY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.tracerStartModeButton);

        // 顶部模式上抬偏移输入框
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

        // 顶部模式上抬偏移标签
        this.addDrawableChild(
            new TextWidget(componentX, tracerTopOffsetY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.tracer_top_offset"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        
        // 颜色配置按钮
        int colorConfigY = getNextButtonY();
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.color_settings"),
            button -> openColorConfig()
        ).dimensions(componentX, colorConfigY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build());
        
        // 完成和取消按钮（并排显示）
        int buttonsY = getNextButtonY();
        int buttonWidth = (COMPONENT_WIDTH - 2) / 2;
        ButtonWidget doneButton = ButtonWidget.builder(
                Text.translatable("screen.multipleplayeresp.config.done"),
                button -> saveAndClose()
        ).dimensions(componentX, buttonsY, buttonWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(doneButton);
        
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.cancel"),
            button -> close()
        ).dimensions(componentX + buttonWidth + 2, buttonsY, buttonWidth, COMPONENT_HEIGHT).build());
        
        // 连接按钮
        int connectY = getNextButtonY();
        this.connectButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.connect"),
            button -> connectToServer()
        ).dimensions(componentX, connectY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.connectButton);
        
        // 更新连接按钮文本
        updateConnectButton();
        
        // 更新方框按钮状态
        updateShowBoxesButton();

        // 更新追踪线按钮状态
        updateShowLinesButton();

        // 更新追踪线起点模式按钮状态
        updateTracerStartModeButton();
        
        // 添加连接状态显示组件
        addConnectionStatusWidget();
        
        // 注册连接状态监听器
        StandaloneMultiPlayerESP.getNetworkManager().addConnectionStatusListener(connectionListener);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1.21.8: 每帧只能 blur 一次，由 super.render() 内部统一调用 renderBackground，此处不再重复调用
        super.render(context, mouseX, mouseY, delta);
        
        // 绘制标题（在布局上方）
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
        // 移除连接状态监听器
        StandaloneMultiPlayerESP.getNetworkManager().removeConnectionStatusListener(connectionListener);
        
        // 恢复原始值
        PlayerESPNetworkManager.setServerURL(this.originalURL);
        StandaloneMultiPlayerESP.getConfig().setRenderDistance(this.originalRenderDistance);
        StandaloneMultiPlayerESP.getConfig().setUpdateInterval(this.originalUpdateInterval);
        StandaloneMultiPlayerESP.getConfig().setShowBoxes(this.originalShowBoxes);
        StandaloneMultiPlayerESP.getConfig().setShowLines(this.originalShowLines);
        StandaloneMultiPlayerESP.getConfig().setTracerStartMode(this.originalTracerStartMode);
        StandaloneMultiPlayerESP.getConfig().setTracerTopOffset(this.originalTracerTopOffset);
        
        MinecraftClient.getInstance().setScreen(this.parent);
    }
    
    private void saveSettings() {
        // 保存设置
        try {
            String url = this.urlField.getText().trim();
            if (!url.isEmpty()) {
                PlayerESPNetworkManager.setServerURL(url);
            }
            
            String renderDistanceStr = this.renderDistanceField.getText().trim();
            if (!renderDistanceStr.isEmpty()) {
                int renderDistance = Integer.parseInt(renderDistanceStr);
                if (renderDistance > 0) {
                    StandaloneMultiPlayerESP.getConfig().setRenderDistance(renderDistance);
                }
            }
            
            String updateIntervalStr = this.updateIntervalField.getText().trim();
            if (!updateIntervalStr.isEmpty()) {
                int updateInterval = Integer.parseInt(updateIntervalStr);
                if (updateInterval > 0) {
                    StandaloneMultiPlayerESP.getConfig().setUpdateInterval(updateInterval);
                }
            }

            String tracerTopOffsetStr = this.tracerTopOffsetField.getText().trim();
            if (!tracerTopOffsetStr.isEmpty()) {
                double tracerTopOffset = Double.parseDouble(tracerTopOffsetStr);
                StandaloneMultiPlayerESP.getConfig().setTracerTopOffset(tracerTopOffset);
            }
            
            // 显示方框/追踪线设置已经在开关按钮中实时保存
            // 保存配置到文件
            StandaloneMultiPlayerESP.getConfig().save();
        } catch (NumberFormatException e) {
            // 如果输入格式不正确，忽略错误并使用原始值
        }
    }
    
    private void saveAndClose() {
        // 保存设置
        saveSettings();
        
        MinecraftClient.getInstance().setScreen(this.parent);
    }
    
    private void connectToServer() {
        // 先保存设置（但不关闭屏幕）
        saveSettings();
        
        // 尝试连接到服务器
        if (StandaloneMultiPlayerESP.isEspEnabled()) {
            // 如果ESP已启用，重新连接
            StandaloneMultiPlayerESP.reconnectToServer();
        } else {
            // 如果ESP未启用，启用它
            StandaloneMultiPlayerESP.setEspEnabled(true);
            StandaloneMultiPlayerESP.reconnectToServer();
        }
    }
    
    private void updateConnectButton() {
        if (StandaloneMultiPlayerESP.isEspEnabled()) {
            this.connectButton.setMessage(Text.translatable("screen.multipleplayeresp.config.reconnect"));
        } else {
            this.connectButton.setMessage(Text.translatable("screen.multipleplayeresp.config.connect"));
        }
    }
    
    private void openColorConfig() {
        MinecraftClient.getInstance().setScreen(new PlayerESPColorConfigScreen(this));
    }
    
    /**
     * 切换方框开关状态
     */
    private void toggleShowBoxes() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isShowBoxes();
        StandaloneMultiPlayerESP.getConfig().setShowBoxes(!currentStatus);
        updateShowBoxesButton();
    }

    /**
     * 切换追踪线开关状态
     */
    private void toggleShowLines() {
        boolean currentStatus = StandaloneMultiPlayerESP.getConfig().isShowLines();
        StandaloneMultiPlayerESP.getConfig().setShowLines(!currentStatus);
        updateShowLinesButton();
    }

    /**
     * 切换追踪线起点模式
     */
    private void toggleTracerStartMode() {
        Config config = StandaloneMultiPlayerESP.getConfig();
        if (config.isTracerStartTop()) {
            config.setTracerStartMode(Config.TRACER_START_CROSSHAIR);
        } else {
            config.setTracerStartMode(Config.TRACER_START_TOP);
        }
        updateTracerStartModeButton();
    }
    
    /**
     * 更新方框按钮显示状态
     */
    private void updateShowBoxesButton() {
        if (this.showBoxesButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isShowBoxes();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.show_boxes").getString();
            if (isEnabled) {
                buttonText += " [ON]";
            } else {
                buttonText += " [OFF]";
            }
            this.showBoxesButton.setMessage(Text.of(buttonText));
        }
    }

    /**
     * 更新追踪线按钮显示状态
     */
    private void updateShowLinesButton() {
        if (this.showLinesButton != null) {
            boolean isEnabled = StandaloneMultiPlayerESP.getConfig().isShowLines();
            String buttonText = Text.translatable("screen.multipleplayeresp.config.show_tracking_lines").getString();
            if (isEnabled) {
                buttonText += " [ON]";
            } else {
                buttonText += " [OFF]";
            }
            this.showLinesButton.setMessage(Text.of(buttonText));
        }
    }

    /**
     * 更新追踪线起点模式按钮显示状态
     */
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
    
    /**
     * 更新连接状态文本
     */
    private void updateConnectionStatus() {
        if (StandaloneMultiPlayerESP.getNetworkManager().isConnected()) {
            this.currentConnectionStatus = "Connected";
        } else {
            this.currentConnectionStatus = "Disconnected";
        }
    }
    
    /**
     * 添加连接状态显示组件
     */
    private void addConnectionStatusWidget() {
        int componentX = getComponentX();
        int statusY = getNextY();
        
        this.connectionStatusWidget = new TextWidget(
            componentX, 
            statusY, 
            COMPONENT_WIDTH, 
            COMPONENT_HEIGHT, 
            Text.translatable("screen.multipleplayeresp.config.connection_status", 
                Text.translatable("connection.status." + this.currentConnectionStatus.toLowerCase())), 
            this.textRenderer
        );
        
        // 设置文本对齐和颜色
        this.connectionStatusWidget.alignCenter();
        updateConnectionStatusWidget();
        
        this.addDrawableChild(this.connectionStatusWidget);
    }
    
    /**
     * 更新连接状态显示组件的颜色和文本
     */
    private void updateConnectionStatusWidget() {
        if (this.connectionStatusWidget != null) {
            // 根据连接状态设置颜色
            int color = this.currentConnectionStatus.equals("Connected") ? 0x00FF00 : 0xFF0000; // 绿色或红色
            
            this.connectionStatusWidget.setTextColor(color);
            
            // 更新文本
            this.connectionStatusWidget.setMessage(
                Text.translatable("screen.multipleplayeresp.config.connection_status", 
                    Text.translatable("connection.status." + this.currentConnectionStatus.toLowerCase()))
            );
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // 更新连接按钮状态
        updateConnectButton();
        
        // 更新方框按钮状态
        updateShowBoxesButton();

        // 更新追踪线按钮状态
        updateShowLinesButton();

        // 更新追踪线起点模式按钮状态
        updateTracerStartModeButton();
    }
}