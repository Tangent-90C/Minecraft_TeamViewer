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
    private ButtonWidget connectButton;
    private ButtonWidget colorSettingsButton;
    private ButtonWidget disconnectButton;
    // 连接状态显示
    private TextWidget connectionStatusWidget;
    private String currentConnectionStatus = "Unknown";
    // 保存原始值，用于取消时恢复
    private final String originalURL;
    private final int originalRenderDistance;
    private final int originalUpdateInterval;
    
    public PlayerESPConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.config.title"));
        this.parent = parent;
        this.originalURL = PlayerESPNetworkManager.getServerURL();
        this.originalRenderDistance = StandaloneMultiPlayerESP.getConfig().getRenderDistance();
        this.originalUpdateInterval = StandaloneMultiPlayerESP.getConfig().getUpdateInterval();
        // 初始化连接状态
        updateConnectionStatus();
    }
    
    // 连接状态监听器实例
    private final ConnectionStatusListener connectionListener = connected -> MinecraftClient.getInstance().execute(() -> {
        updateConnectionStatus();
        updateConnectionStatusWidget();
    });
    
    @Override
    protected void init() {
        super.init();
        
        // 服务器URL输入框
        this.urlField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 100,
            this.height / 4 + 20,
            200,
            20,
            Text.translatable("screen.multipleplayeresp.config.url")
        );
        this.urlField.setText(PlayerESPNetworkManager.getServerURL());
        this.urlField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.url_hint"));
        this.addDrawableChild(this.urlField);
        
        // 渲染距离输入框
        this.renderDistanceField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 100,
            this.height / 4 + 60,
            200,
            20,
            Text.translatable("screen.multipleplayeresp.config.render_distance")
        );
        this.renderDistanceField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getRenderDistance()));
        this.renderDistanceField.setMaxLength(3); // 渲染距离最大3位数字
        this.renderDistanceField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.render_distance_hint"));
        this.addDrawableChild(this.renderDistanceField);
        
        // 上报频率输入框
        this.updateIntervalField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 100,
            this.height / 4 + 100,
            200,
            20,
            Text.translatable("screen.multipleplayeresp.config.update_interval")
        );
        this.updateIntervalField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getUpdateInterval()));
        this.updateIntervalField.setMaxLength(4); // 上报频率最大4位数字
        this.updateIntervalField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.update_interval_hint"));
        this.addDrawableChild(this.updateIntervalField);
        
        // 颜色配置按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.color_settings"),
            button -> openColorConfig()
        ).dimensions(this.width / 2 - 100, this.height / 4 + 140, 200, 20).build());
        
        // 完成按钮
        ButtonWidget doneButton = ButtonWidget.builder(
                Text.translatable("screen.multipleplayeresp.config.done"),
                button -> saveAndClose()
        ).dimensions(this.width / 2 - 100, this.height / 4 + 170, 98, 20).build();
        this.addDrawableChild(doneButton);
        
        // 取消按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.cancel"),
            button -> close()
        ).dimensions(this.width / 2 + 2, this.height / 4 + 170, 98, 20).build());
        
        // 连接按钮
        this.connectButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.connect"),
            button -> connectToServer()
        ).dimensions(this.width / 2 - 100, this.height / 4 + 200, 200, 20).build();
        this.addDrawableChild(this.connectButton);
        
        // 输入框上方的标签（最后添加以确保渲染在最上层）
        int left = this.width / 2 - 100;
        this.addDrawableChild(
            new TextWidget(left, this.height / 4 + 8, 200, 12, Text.translatable("screen.multipleplayeresp.config.url"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        this.addDrawableChild(
            new TextWidget(left, this.height / 4 + 48, 200, 12, Text.translatable("screen.multipleplayeresp.config.render_distance"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        this.addDrawableChild(
            new TextWidget(left, this.height / 4 + 88, 200, 12, Text.translatable("screen.multipleplayeresp.config.update_interval"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        
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
        
        // 绘制标签
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            this.height / 4 - 40,
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
        int left = this.width / 2 - 100;
        int top = this.height / 4 + 230; // 在连接按钮下方
        
        this.connectionStatusWidget = new TextWidget(
            left, 
            top, 
            200, 
            20, 
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
    }
}