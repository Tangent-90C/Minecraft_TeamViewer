package fun.prof_chen.teamviewer.multipleplayeresp.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;
import fun.prof_chen.teamviewer.multipleplayeresp.core.StandaloneMultiPlayerESP;
import fun.prof_chen.teamviewer.multipleplayeresp.network.PlayerESPNetworkManager;
import fun.prof_chen.teamviewer.multipleplayeresp.network.PlayerESPNetworkManager.ConnectionStatusListener;

public class PlayerESPConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget urlField;
    private TextFieldWidget roomCodeField;
    private ButtonWidget connectButton;
    private ButtonWidget displaySettingsButton;
    private ButtonWidget networkSettingsButton;
    private ButtonWidget disconnectButton;
    // 连接状态显示
    private TextWidget connectionStatusWidget;
    private int connectionStatusX;
    private int connectionStatusY;
    private String currentConnectionStatus = "Unknown";
    // 保存原始值，用于取消时恢复
    private final String originalURL;
    private final String originalRoomCode;
    private final int originalRenderDistance;
    private final int originalUpdateInterval;
    private final boolean originalShowBoxes;
    private final boolean originalShowLines;
    private final boolean originalUploadEntities;
    private final boolean originalUploadSharedWaypoints;
    private final boolean originalShowSharedWaypoints;
    private final boolean originalUseSystemProxy;
    private final String originalTracerStartMode;
    private final double originalTracerTopOffset;
    
    // 自动布局相关变量
    private static final int COMPONENT_WIDTH = 200;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private static final int BUTTON_SPACING = 25;
    private static final int URL_MAX_LENGTH = 2048;
    private static final int ROOM_CODE_MAX_LENGTH = 64;
    private int startY;
    private int currentY;
    
    public PlayerESPConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.config.title"));
        this.parent = parent;
        this.originalURL = PlayerESPNetworkManager.getServerURL();
        this.originalRoomCode = PlayerESPNetworkManager.getRoomCode();
        this.originalRenderDistance = StandaloneMultiPlayerESP.getConfig().getRenderDistance();
        this.originalUpdateInterval = StandaloneMultiPlayerESP.getConfig().getUpdateInterval();
        this.originalShowBoxes = StandaloneMultiPlayerESP.getConfig().isShowBoxes();
        this.originalShowLines = StandaloneMultiPlayerESP.getConfig().isShowLines();
        this.originalUploadEntities = StandaloneMultiPlayerESP.getConfig().isUploadEntities();
        this.originalUploadSharedWaypoints = StandaloneMultiPlayerESP.getConfig().isUploadSharedWaypoints();
        this.originalShowSharedWaypoints = StandaloneMultiPlayerESP.getConfig().isShowSharedWaypoints();
        this.originalUseSystemProxy = StandaloneMultiPlayerESP.getConfig().isUseSystemProxy();
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
        totalHeight += COMPONENT_SPACING; // 房间号输入框组
        totalHeight += BUTTON_SPACING;    // 显示设置/网络设置按钮行
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
        this.urlField.setMaxLength(URL_MAX_LENGTH);
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

        // 房间号输入框
        int roomCodeY = getNextY();
        this.roomCodeField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            roomCodeY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.config.room_code")
        );
        this.roomCodeField.setMaxLength(ROOM_CODE_MAX_LENGTH);
        this.roomCodeField.setText(PlayerESPNetworkManager.getRoomCode());
        this.roomCodeField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.room_code_hint"));
        this.addDrawableChild(this.roomCodeField);

        this.addDrawableChild(
            new TextWidget(componentX, roomCodeY - LABEL_SPACING, COMPONENT_WIDTH, 12,
                Text.translatable("screen.multipleplayeresp.config.room_code"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        
        // 显示设置/网络设置按钮（左右布局）
        int settingsY = getNextButtonY();
        int settingsButtonWidth = (COMPONENT_WIDTH - 2) / 2;
        this.displaySettingsButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.display_settings"),
            button -> openDisplaySettings()
        ).dimensions(componentX, settingsY, settingsButtonWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.displaySettingsButton);

        this.networkSettingsButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.network_settings"),
            button -> openNetworkSettings()
        ).dimensions(componentX + settingsButtonWidth + 2, settingsY, settingsButtonWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.networkSettingsButton);
        
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
        int connectButtonWidth = (COMPONENT_WIDTH - 2) / 2;
        this.connectButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.connect"),
            button -> connectToServer()
        ).dimensions(componentX, connectY, connectButtonWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.connectButton);

        this.disconnectButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.disconnect"),
            button -> disconnectFromServer()
        ).dimensions(componentX + connectButtonWidth + 2, connectY, connectButtonWidth, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.disconnectButton);
        
        // 更新连接按钮文本
        updateConnectButton();
        
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

        if ("Failed".equals(this.currentConnectionStatus) && isMouseOverConnectionStatus(mouseX, mouseY)) {
            String reason = StandaloneMultiPlayerESP.getNetworkManager().getLastConnectionError();
            if (reason == null || reason.isBlank()) {
                reason = Text.translatable("screen.multipleplayeresp.config.unknown_error").getString();
            }
            context.drawTooltip(
                this.textRenderer,
                splitTooltipLines(reason, 48),
                mouseX,
                mouseY
            );
        }
        
    }
    
    @Override
    public void close() {
        // 移除连接状态监听器
        StandaloneMultiPlayerESP.getNetworkManager().removeConnectionStatusListener(connectionListener);
        
        // 恢复原始值
        PlayerESPNetworkManager.setServerURL(this.originalURL);
        PlayerESPNetworkManager.setRoomCode(this.originalRoomCode);
        StandaloneMultiPlayerESP.getConfig().setRenderDistance(this.originalRenderDistance);
        StandaloneMultiPlayerESP.getConfig().setUpdateInterval(this.originalUpdateInterval);
        StandaloneMultiPlayerESP.getConfig().setShowBoxes(this.originalShowBoxes);
        StandaloneMultiPlayerESP.getConfig().setShowLines(this.originalShowLines);
        StandaloneMultiPlayerESP.getConfig().setUploadEntities(this.originalUploadEntities);
        StandaloneMultiPlayerESP.getConfig().setUploadSharedWaypoints(this.originalUploadSharedWaypoints);
        StandaloneMultiPlayerESP.getConfig().setShowSharedWaypoints(this.originalShowSharedWaypoints);
        StandaloneMultiPlayerESP.getConfig().setUseSystemProxy(this.originalUseSystemProxy);
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
            String roomCode = this.roomCodeField.getText().trim();
            PlayerESPNetworkManager.setRoomCode(roomCode);
            
            // 显示/网络开关设置在二级页面中实时保存
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

        if (this.disconnectButton != null) {
            this.disconnectButton.active = StandaloneMultiPlayerESP.isEspEnabled()
                || StandaloneMultiPlayerESP.getNetworkManager().isConnected();
        }
    }

    private void disconnectFromServer() {
        StandaloneMultiPlayerESP.setEspEnabled(false);
        StandaloneMultiPlayerESP.getNetworkManager().disconnect();
        updateConnectionStatus();
        updateConnectionStatusWidget();
        updateConnectButton();
    }
    
    private void openDisplaySettings() {
        MinecraftClient.getInstance().setScreen(new PlayerESPDisplayConfigScreen(this));
    }

    private void openNetworkSettings() {
        MinecraftClient.getInstance().setScreen(new PlayerESPNetworkConfigScreen(this));
    }

    /**
     * 更新连接状态文本
     */
    private void updateConnectionStatus() {
        PlayerESPNetworkManager networkManager = StandaloneMultiPlayerESP.getNetworkManager();
        if (networkManager.isConnected()) {
            this.currentConnectionStatus = "Connected";
        } else if (!networkManager.getLastConnectionError().isBlank()) {
            this.currentConnectionStatus = "Failed";
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
        this.connectionStatusX = componentX;
        this.connectionStatusY = statusY;
        
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
            int color;
            if (this.currentConnectionStatus.equals("Connected")) {
                color = 0x00FF00;
            } else if (this.currentConnectionStatus.equals("Failed")) {
                color = 0xFFAA00;
            } else {
                color = 0xFF0000;
            }
            
            this.connectionStatusWidget.setTextColor(color);
            
            // 更新文本
            if (this.currentConnectionStatus.equals("Failed")) {
                this.connectionStatusWidget.setMessage(
                    Text.translatable("screen.multipleplayeresp.config.connection_failed_short")
                );
            } else {
                this.connectionStatusWidget.setMessage(
                    Text.translatable("screen.multipleplayeresp.config.connection_status",
                        Text.translatable("connection.status." + this.currentConnectionStatus.toLowerCase()))
                );
            }
        }
    }

    private boolean isMouseOverConnectionStatus(int mouseX, int mouseY) {
        return this.connectionStatusWidget != null
            && mouseX >= this.connectionStatusX
            && mouseX < this.connectionStatusX + COMPONENT_WIDTH
            && mouseY >= this.connectionStatusY
            && mouseY < this.connectionStatusY + COMPONENT_HEIGHT;
    }

    private List<Text> splitTooltipLines(String content, int maxCharsPerLine) {
        List<Text> lines = new ArrayList<>();
        if (content == null || content.isBlank()) {
            lines.add(Text.translatable("screen.multipleplayeresp.config.unknown_error"));
            return lines;
        }

        String[] rawLines = content.split("\\R");
        for (String rawLine : rawLines) {
            if (rawLine.length() <= maxCharsPerLine) {
                lines.add(Text.of(rawLine));
                continue;
            }

            int start = 0;
            while (start < rawLine.length()) {
                int end = Math.min(start + maxCharsPerLine, rawLine.length());
                lines.add(Text.of(rawLine.substring(start, end)));
                start = end;
            }
        }

        if (lines.isEmpty()) {
            lines.add(Text.of(content));
        }
        return lines;
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // 更新连接按钮状态
        updateConnectButton();
    }
}