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
    
    // 自动布局相关变量
    private static final int COMPONENT_WIDTH = 200;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_SPACING = 30;
    private static final int LABEL_SPACING = 12;
    private int startY;
    private int currentY;
    
    public PlayerESPColorConfigScreen(Screen parent) {
        super(Text.translatable("screen.multipleplayeresp.color_config.title"));
        this.parent = parent;
        this.originalBoxColor = StandaloneMultiPlayerESP.getConfig().getBoxColor();
        this.originalLineColor = StandaloneMultiPlayerESP.getConfig().getLineColor();
    }
    
    /**
     * 计算起始Y坐标，使所有组件居中显示
     */
    private void calculateLayout() {
        // 计算总高度需求
        int totalHeight = 0;
        totalHeight += COMPONENT_SPACING; // 标题间距
        totalHeight += COMPONENT_SPACING; // 方框颜色输入框组
        totalHeight += COMPONENT_SPACING; // 线条颜色输入框组
        totalHeight += COMPONENT_SPACING; // 按钮行
        
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
     * 获取组件的X坐标（居中）
     */
    private int getComponentX() {
        return (this.width - COMPONENT_WIDTH) / 2;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 计算自动布局参数
        calculateLayout();
        
        // 跳过标题间距
        currentY += COMPONENT_SPACING;
        
        // 方框颜色输入框
        int componentX = getComponentX();
        int boxColorY = getNextY();
        this.boxColorField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            boxColorY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.color_config.box_color")
        );
        this.boxColorField.setText(String.format("0x%08X", StandaloneMultiPlayerESP.getConfig().getBoxColor()));
        this.addDrawableChild(this.boxColorField);
        
        // 方框颜色标签
        this.addDrawableChild(
            new net.minecraft.client.gui.widget.TextWidget(componentX, boxColorY - LABEL_SPACING, COMPONENT_WIDTH, 12, 
                Text.translatable("screen.multipleplayeresp.color_config.box_color"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xA0A0A0)
        );
        
        // 线条颜色输入框
        int lineColorY = getNextY();
        this.lineColorField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            lineColorY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.color_config.line_color")
        );
        this.lineColorField.setText(String.format("0x%08X", StandaloneMultiPlayerESP.getConfig().getLineColor()));
        this.addDrawableChild(this.lineColorField);
        
        // 线条颜色标签
        this.addDrawableChild(
            new net.minecraft.client.gui.widget.TextWidget(componentX, lineColorY - LABEL_SPACING, COMPONENT_WIDTH, 12, 
                Text.translatable("screen.multipleplayeresp.color_config.line_color"), this.textRenderer)
                .alignLeft()
                .setTextColor(0xA0A0A0)
        );
        
        // 完成和取消按钮（并排显示）
        int buttonsY = getNextY();
        int buttonWidth = (COMPONENT_WIDTH - 2) / 2;
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.color_config.done"),
            button -> saveAndClose()
        ).dimensions(componentX, buttonsY, buttonWidth, COMPONENT_HEIGHT).build());
        
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("screen.multipleplayeresp.color_config.cancel"),
            button -> close()
        ).dimensions(componentX + buttonWidth + 2, buttonsY, buttonWidth, COMPONENT_HEIGHT).build());
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1.21.8: 每帧只能 blur 一次，由 super.render() 内部统一调用 renderBackground
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
            
            // 保存配置到文件
            StandaloneMultiPlayerESP.getConfig().save();
        } catch (NumberFormatException e) {
            // 如果输入格式不正确，忽略错误并使用原始值
        }
        
        MinecraftClient.getInstance().setScreen(this.parent);
    }
}