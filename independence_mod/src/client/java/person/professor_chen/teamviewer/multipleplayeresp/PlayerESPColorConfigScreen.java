package person.professor_chen.teamviewer.multipleplayeresp;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

public class PlayerESPColorConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget boxColorField;
    private TextFieldWidget lineColorField;
    
    // 保存原始值，用于取消时恢复
    private final int originalBoxColor;
    private final int originalLineColor;
    
    public PlayerESPColorConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.color_config.title"));
        this.parent = parent;
        this.originalBoxColor = StandaloneMultiPlayerESP.getConfig().getBoxColor();
        this.originalLineColor = StandaloneMultiPlayerESP.getConfig().getLineColor();
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 方框颜色输入框
        this.boxColorField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 100,
            this.height / 4 + 20,
            200,
            20,
            Text.translatable("screen.multipleplayeresp.color_config.box_color")
        );
        this.boxColorField.setText(String.format("0x%08X", StandaloneMultiPlayerESP.getConfig().getBoxColor()));
        this.addDrawableChild(this.boxColorField);
        
        // 线条颜色输入框
        this.lineColorField = new TextFieldWidget(
            this.textRenderer,
            this.width / 2 - 100,
            this.height / 4 + 60,
            200,
            20,
            Text.translatable("screen.multipleplayeresp.color_config.line_color")
        );
        this.lineColorField.setText(String.format("0x%08X", StandaloneMultiPlayerESP.getConfig().getLineColor()));
        this.addDrawableChild(this.lineColorField);
        
        // 完成按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.color_config.done"),
            button -> saveAndClose()
        ).dimensions(this.width / 2 - 100, this.height / 4 + 100, 98, 20).build());
        
        // 取消按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.color_config.cancel"),
            button -> close()
        ).dimensions(this.width / 2 + 2, this.height / 4 + 100, 98, 20).build());
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1.21.8: 每帧只能 blur 一次，由 super.render() 内部统一调用 renderBackground
        super.render(context, mouseX, mouseY, delta);
        
        // 绘制标签
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            this.height / 4 - 40,
            0xFFFFFF
        );
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.translatable("screen.multipleplayeresp.color_config.box_color"),
            this.width / 2 - 100,
            this.height / 4 + 10,
            0xA0A0A0
        );
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.translatable("screen.multipleplayeresp.color_config.line_color"),
            this.width / 2 - 100,
            this.height / 4 + 50,
            0xA0A0A0
        );
    }
    
    @Override
    public void close() {
        // 恢复原始值
        StandaloneMultiPlayerESP.getConfig().setBoxColor(this.originalBoxColor);
        StandaloneMultiPlayerESP.getConfig().setLineColor(this.originalLineColor);
        
        MinecraftClient.getInstance().setScreen(this.parent);
    }
    
    private void saveAndClose() {
        // 保存设置
        try {
            String boxColorStr = this.boxColorField.getText().trim();
            if (boxColorStr.startsWith("0x") || boxColorStr.startsWith("0X")) {
                int boxColor = (int) Long.parseLong(boxColorStr.substring(2), 16);
                StandaloneMultiPlayerESP.getConfig().setBoxColor(boxColor);
            } else if (!boxColorStr.isEmpty()) {
                int boxColor = (int) Long.parseLong(boxColorStr, 16);
                StandaloneMultiPlayerESP.getConfig().setBoxColor(boxColor);
            }
            
            String lineColorStr = this.lineColorField.getText().trim();
            if (lineColorStr.startsWith("0x") || lineColorStr.startsWith("0X")) {
                int lineColor = (int) Long.parseLong(lineColorStr.substring(2), 16);
                StandaloneMultiPlayerESP.getConfig().setLineColor(lineColor);
            } else if (!lineColorStr.isEmpty()) {
                int lineColor = (int) Long.parseLong(lineColorStr, 16);
                StandaloneMultiPlayerESP.getConfig().setLineColor(lineColor);
            }
        } catch (NumberFormatException e) {
            // 如果输入格式不正确，忽略错误并使用原始值
        }
        
        MinecraftClient.getInstance().setScreen(this.parent);
    }
}