package person.professor_chen.teamviewer.multipleplayeresp;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import person.professor_chen.teamviewer.multipleplayeresp.StandaloneMultiPlayerESP;

public class PlayerESPConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget ipField;
    private TextFieldWidget portField;
    private TextFieldWidget renderDistanceField;
    private ButtonWidget doneButton;
    private ButtonWidget connectButton;
    private ButtonWidget colorSettingsButton;
    private ButtonWidget disconnectButton;
    // 保存原始值，用于取消时恢复
    private final String originalIP;
    private final int originalPort;
    private final int originalRenderDistance;
    
    public PlayerESPConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.config.title"));
        this.parent = parent;
        this.originalIP = PlayerESPNetworkManager.getServerIP();
        this.originalPort = PlayerESPNetworkManager.getServerPort();
        this.originalRenderDistance = StandaloneMultiPlayerESP.getConfig().getRenderDistance();
    }
    
    @Override
    protected void init() {
        super.init();
        
        // IP地址输入框
        this.ipField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 100,
            this.height / 4 + 20,
            200,
            20,
            Text.translatable("screen.multipleplayeresp.config.ip")
        );
        this.ipField.setText(PlayerESPNetworkManager.getServerIP());
        this.ipField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.ip_hint"));
        this.addDrawableChild(this.ipField);
        
        // 端口输入框
        this.portField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 100,
            this.height / 4 + 60,
            200,
            20,
            Text.translatable("screen.multipleplayeresp.config.port")
        );
        this.portField.setText(String.valueOf(PlayerESPNetworkManager.getServerPort()));
        this.portField.setMaxLength(5); // 端口号最大5位数字
        this.portField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.port_hint"));
        this.addDrawableChild(this.portField);
        
        // 渲染距离输入框
        this.renderDistanceField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 100,
            this.height / 4 + 100,
            200,
            20,
            Text.translatable("screen.multipleplayeresp.config.render_distance")
        );
        this.renderDistanceField.setText(String.valueOf(StandaloneMultiPlayerESP.getConfig().getRenderDistance()));
        this.renderDistanceField.setMaxLength(3); // 渲染距离最大3位数字
        this.renderDistanceField.setPlaceholder(Text.translatable("screen.multipleplayeresp.config.render_distance_hint"));
        this.addDrawableChild(this.renderDistanceField);
        
        // 颜色配置按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.color_settings"),
            button -> openColorConfig()
        ).dimensions(this.width / 2 - 100, this.height / 4 + 140, 200, 20).build());
        
        // 完成按钮
        this.doneButton = ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.config.done"),
            button -> saveAndClose()
        ).dimensions(this.width / 2 - 100, this.height / 4 + 170, 98, 20).build();
        this.addDrawableChild(this.doneButton);
        
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
            new TextWidget(left, this.height / 4 + 8, 200, 12, Text.translatable("screen.multipleplayeresp.config.ip"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        this.addDrawableChild(
            new TextWidget(left, this.height / 4 + 48, 200, 12, Text.translatable("screen.multipleplayeresp.config.port"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        this.addDrawableChild(
            new TextWidget(left, this.height / 4 + 88, 200, 12, Text.translatable("screen.multipleplayeresp.config.render_distance"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xFFFFFF)
        );
        
        // 更新连接按钮文本
        updateConnectButton();
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
        // 恢复原始值
        PlayerESPNetworkManager.setServerIP(this.originalIP);
        PlayerESPNetworkManager.setServerPort(this.originalPort);
        StandaloneMultiPlayerESP.getConfig().setRenderDistance(this.originalRenderDistance);
        
        MinecraftClient.getInstance().setScreen(this.parent);
    }
    
    private void saveAndClose() {
        // 保存设置
        try {
            String ip = this.ipField.getText().trim();
            if (!ip.isEmpty()) {
                PlayerESPNetworkManager.setServerIP(ip);
            }
            
            String portStr = this.portField.getText().trim();
            if (!portStr.isEmpty()) {
                int port = Integer.parseInt(portStr);
                if (port > 0 && port <= 65535) {
                    PlayerESPNetworkManager.setServerPort(port);
                }
            }
            
            String renderDistanceStr = this.renderDistanceField.getText().trim();
            if (!renderDistanceStr.isEmpty()) {
                int renderDistance = Integer.parseInt(renderDistanceStr);
                if (renderDistance > 0) {
                    StandaloneMultiPlayerESP.getConfig().setRenderDistance(renderDistance);
                }
            }
        } catch (NumberFormatException e) {
            // 如果输入格式不正确，忽略错误并使用原始值
        }
        
        MinecraftClient.getInstance().setScreen(this.parent);
    }
    
    private void connectToServer() {
        // 先保存设置
        saveAndClose();
        
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
    
    @Override
    public void tick() {
        super.tick();
        
        // 更新连接按钮状态
        updateConnectButton();
    }
}