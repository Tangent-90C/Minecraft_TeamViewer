package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.client.PlayerProcesses;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Native 26.1 configuration entry screen. Configuration storage remains the common Config model.
 */
public final class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("title.mc_teamviewer.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(statusLabel(), button -> {
                    PlayerProcesses.setModEnable(!PlayerProcesses.isModEnable());
                    button.setMessage(statusLabel());
                })
                .bounds(center - 100, height / 2 - 36, 200, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("button.mc_teamviewer.reconnect"), button ->
                        PlayerProcesses.reconnectToServer())
                .bounds(center - 100, height / 2 - 8, 200, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(center - 100, height / 2 + 40, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 28, 0xFFFFFFFF);
        String endpoint = PlayerProcesses.getConfig().getServerURL();
        graphics.centeredText(font, endpoint, width / 2, height / 2 + 18, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static Component statusLabel() {
        return Component.literal(PlayerProcesses.isModEnable() ? "TeamViewRelay: ON" : "TeamViewRelay: OFF");
    }
}
