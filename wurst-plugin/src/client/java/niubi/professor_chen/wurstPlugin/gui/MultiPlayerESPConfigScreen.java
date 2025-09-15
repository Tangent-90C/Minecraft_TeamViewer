package niubi.professor_chen.wurstPlugin.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;
import niubi.professor_chen.wurstPlugin.config.MultiPlayerESPConfig;

public class MultiPlayerESPConfigScreen extends Screen {
    private final Screen parent;
    private final MultiPlayerESPConfig config = MultiPlayerESPConfig.getInstance();
    
    // Widgets
    private CheckboxWidget networkSyncCheckbox;
    private CheckboxWidget enableWurstMixinCheckbox;
    private TextFieldWidget serverIPField;
    private TextFieldWidget serverPortField;
    private ButtonWidget doneButton;
    
    public MultiPlayerESPConfigScreen(Screen parent) {
        super(Text.literal("MultiPlayer ESP Config"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        // 添加网络同步复选框
        networkSyncCheckbox = CheckboxWidget.builder(Text.literal("Network Sync"), this.textRenderer)
                .pos(this.width / 2 - 100, 50)
                .build();
        if (config.isNetworkSyncEnabled() && !networkSyncCheckbox.isChecked()) {
            networkSyncCheckbox.onPress();
        }
        this.addDrawableChild(networkSyncCheckbox);
        
        // 添加启用Wurst Mixin复选框
        enableWurstMixinCheckbox = CheckboxWidget.builder(Text.literal("Enable Wurst Mixin"), this.textRenderer)
                .pos(this.width / 2 - 100, 75)
                .build();
        if (config.isWurstMixinEnabled() && !enableWurstMixinCheckbox.isChecked()) {
            enableWurstMixinCheckbox.onPress();
        }
        this.addDrawableChild(enableWurstMixinCheckbox);
        
        // 添加服务器IP文本框
        serverIPField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 100, 200, 20, Text.literal("Server IP"));
        serverIPField.setText(config.getServerIPValue());
        this.addDrawableChild(serverIPField);
        
        // 添加服务器端口文本框
        serverPortField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 130, 200, 20, Text.literal("Server Port"));
        serverPortField.setText(config.getServerPortValue());
        this.addDrawableChild(serverPortField);
        
        // 添加完成按钮
        doneButton = ButtonWidget.builder(Text.literal("Done"), button -> saveAndClose())
                .position(this.width / 2 - 100, this.height - 30)
                .size(200, 20)
                .build();
        this.addDrawableChild(doneButton);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void saveAndClose() {
        // 保存设置
        boolean mixinWasEnabled = config.isWurstMixinEnabled();
        boolean mixinIsEnabled = enableWurstMixinCheckbox.isChecked();
        
        config.setNetworkSync(networkSyncCheckbox.isChecked());
        config.setEnableWurstMixin(enableWurstMixinCheckbox.isChecked());
        config.setServerIP(serverIPField.getText());
        config.setServerPort(serverPortField.getText());
        
        // 如果 mixin 设置发生了改变，则提示用户需要重启
        if (mixinWasEnabled != mixinIsEnabled) {
            // 这里可以添加一个提示，告知用户需要重启才能使设置生效
        }
        
        // 关闭界面
        this.client.setScreen(parent);
    }
    
    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}