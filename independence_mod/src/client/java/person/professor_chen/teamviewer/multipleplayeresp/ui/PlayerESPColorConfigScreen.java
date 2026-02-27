package person.professor_chen.teamviewer.multipleplayeresp.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import person.professor_chen.teamviewer.multipleplayeresp.core.StandaloneMultiPlayerESP;

public class PlayerESPColorConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget boxColorField;
    private TextFieldWidget lineColorField;
    private TextFieldWidget friendlyTeamColorField;
    private TextFieldWidget neutralTeamColorField;
    private TextFieldWidget enemyTeamColorField;

    private final int originalBoxColor;
    private final int originalLineColor;
    private final int originalFriendlyTeamColor;
    private final int originalNeutralTeamColor;
    private final int originalEnemyTeamColor;

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
        this.originalFriendlyTeamColor = StandaloneMultiPlayerESP.getConfig().getFriendlyTeamColor();
        this.originalNeutralTeamColor = StandaloneMultiPlayerESP.getConfig().getNeutralTeamColor();
        this.originalEnemyTeamColor = StandaloneMultiPlayerESP.getConfig().getEnemyTeamColor();
    }

    private void calculateLayout() {
        int totalHeight = 0;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;
        totalHeight += COMPONENT_SPACING;

        startY = (this.height - totalHeight) / 2;
        currentY = startY;
    }

    private int getNextY() {
        int result = currentY;
        currentY += COMPONENT_SPACING;
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
        this.addDrawableChild(new TextWidget(componentX, boxColorY - LABEL_SPACING, COMPONENT_WIDTH, 12,
            Text.translatable("screen.multipleplayeresp.color_config.box_color"), this.textRenderer)
            .alignLeft()
            .setTextColor(0xA0A0A0));

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
        this.addDrawableChild(new TextWidget(componentX, lineColorY - LABEL_SPACING, COMPONENT_WIDTH, 12,
            Text.translatable("screen.multipleplayeresp.color_config.line_color"), this.textRenderer)
            .alignLeft()
            .setTextColor(0xA0A0A0));

        int friendlyColorY = getNextY();
        this.friendlyTeamColorField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            friendlyColorY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.color_config.friendly_team_color")
        );
        this.friendlyTeamColorField.setText(String.format("0x%08X", StandaloneMultiPlayerESP.getConfig().getFriendlyTeamColor()));
        this.addDrawableChild(this.friendlyTeamColorField);
        this.addDrawableChild(new TextWidget(componentX, friendlyColorY - LABEL_SPACING, COMPONENT_WIDTH, 12,
            Text.translatable("screen.multipleplayeresp.color_config.friendly_team_color"), this.textRenderer)
            .alignLeft()
            .setTextColor(0xA0A0A0));

        int neutralColorY = getNextY();
        this.neutralTeamColorField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            neutralColorY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.color_config.neutral_team_color")
        );
        this.neutralTeamColorField.setText(String.format("0x%08X", StandaloneMultiPlayerESP.getConfig().getNeutralTeamColor()));
        this.addDrawableChild(this.neutralTeamColorField);
        this.addDrawableChild(new TextWidget(componentX, neutralColorY - LABEL_SPACING, COMPONENT_WIDTH, 12,
            Text.translatable("screen.multipleplayeresp.color_config.neutral_team_color"), this.textRenderer)
            .alignLeft()
            .setTextColor(0xA0A0A0));

        int enemyColorY = getNextY();
        this.enemyTeamColorField = new TextFieldWidget(
            this.textRenderer,
            componentX,
            enemyColorY,
            COMPONENT_WIDTH,
            COMPONENT_HEIGHT,
            Text.translatable("screen.multipleplayeresp.color_config.enemy_team_color")
        );
        this.enemyTeamColorField.setText(String.format("0x%08X", StandaloneMultiPlayerESP.getConfig().getEnemyTeamColor()));
        this.addDrawableChild(this.enemyTeamColorField);
        this.addDrawableChild(new TextWidget(componentX, enemyColorY - LABEL_SPACING, COMPONENT_WIDTH, 12,
            Text.translatable("screen.multipleplayeresp.color_config.enemy_team_color"), this.textRenderer)
            .alignLeft()
            .setTextColor(0xA0A0A0));

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
        StandaloneMultiPlayerESP.getConfig().setBoxColor(this.originalBoxColor);
        StandaloneMultiPlayerESP.getConfig().setLineColor(this.originalLineColor);
        StandaloneMultiPlayerESP.getConfig().setFriendlyTeamColor(this.originalFriendlyTeamColor);
        StandaloneMultiPlayerESP.getConfig().setNeutralTeamColor(this.originalNeutralTeamColor);
        StandaloneMultiPlayerESP.getConfig().setEnemyTeamColor(this.originalEnemyTeamColor);

        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void saveAndClose() {
        try {
            Integer boxColor = parseColorValue(this.boxColorField.getText());
            if (boxColor != null) {
                StandaloneMultiPlayerESP.getConfig().setBoxColor(boxColor);
            }

            Integer lineColor = parseColorValue(this.lineColorField.getText());
            if (lineColor != null) {
                StandaloneMultiPlayerESP.getConfig().setLineColor(lineColor);
            }

            Integer friendlyColor = parseColorValue(this.friendlyTeamColorField.getText());
            if (friendlyColor != null) {
                StandaloneMultiPlayerESP.getConfig().setFriendlyTeamColor(friendlyColor);
            }

            Integer neutralColor = parseColorValue(this.neutralTeamColorField.getText());
            if (neutralColor != null) {
                StandaloneMultiPlayerESP.getConfig().setNeutralTeamColor(neutralColor);
            }

            Integer enemyColor = parseColorValue(this.enemyTeamColorField.getText());
            if (enemyColor != null) {
                StandaloneMultiPlayerESP.getConfig().setEnemyTeamColor(enemyColor);
            }

            StandaloneMultiPlayerESP.getConfig().save();
        } catch (NumberFormatException e) {
        }

        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private Integer parseColorValue(String rawText) {
        if (rawText == null) {
            return null;
        }
        String text = rawText.trim();
        if (text.isEmpty()) {
            return null;
        }

        if (text.startsWith("0x") || text.startsWith("0X")) {
            return (int) Long.parseLong(text.substring(2), 16);
        }
        if (text.startsWith("#")) {
            String hex = text.substring(1);
            if (hex.length() == 6) {
                return (0xFF << 24) | Integer.parseInt(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
            return null;
        }
        return (int) Long.parseLong(text, 16);
    }
}
