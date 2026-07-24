package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.client.PlayerProcesses;
import fun.prof_chen.teamviewer.main_code.config.Config;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Native 26.1 configuration entry screen. Configuration storage remains the common Config model.
 */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private EditBox serverUrl;
    private EditBox roomCode;
    private Checkbox autoConnect;
    private Checkbox uploadEntities;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("title.mc_teamviewer.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        Config config = PlayerProcesses.getConfig();
        serverUrl = new EditBox(font, center - 150, 54, 300, 20,
                Component.translatable("screen.mc_teamviewer.config.url"));
        serverUrl.setMaxLength(512);
        serverUrl.setValue(config.getServerURL());
        serverUrl.setHint(Component.translatable("screen.mc_teamviewer.config.url_hint"));
        addRenderableWidget(serverUrl);

        roomCode = new EditBox(font, center - 150, 96, 300, 20,
                Component.translatable("screen.mc_teamviewer.config.room_code"));
        roomCode.setMaxLength(64);
        roomCode.setValue(config.getRoomCode());
        roomCode.setHint(Component.translatable("screen.mc_teamviewer.config.room_code_hint"));
        addRenderableWidget(roomCode);

        autoConnect = Checkbox.builder(
                        Component.translatable("screen.mc_teamviewer.config.auto_connect_on_multiplayer_join"), font)
                .pos(center - 150, 126)
                .selected(config.isAutoConnectOnMultiplayerJoin())
                .build();
        addRenderableWidget(autoConnect);
        uploadEntities = Checkbox.builder(
                        Component.translatable("screen.mc_teamviewer.config.upload_entities"), font)
                .pos(center - 150, 150)
                .selected(config.isUploadEntities())
                .build();
        addRenderableWidget(uploadEntities);

        addRenderableWidget(Button.builder(statusLabel(), button -> {
                    PlayerProcesses.setModEnable(!PlayerProcesses.isModEnable());
                    button.setMessage(statusLabel());
                })
                .bounds(center - 150, height - 76, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mc_teamviewer.config.reconnect"), button ->
                        PlayerProcesses.reconnectToServer())
                .bounds(center - 48, height - 76, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mc_teamviewer.config.done"), button -> saveAndClose())
                .bounds(center + 54, height - 76, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mc_teamviewer.config.cancel"), button -> onClose())
                .bounds(center - 100, height - 48, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 28, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.mc_teamviewer.config.url"), width / 2 - 150, 42, 0xFFAAAAAA);
        graphics.text(font, Component.translatable("screen.mc_teamviewer.config.room_code"), width / 2 - 150, 84, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static Component statusLabel() {
        return Component.literal(PlayerProcesses.isModEnable() ? "TeamViewRelay: ON" : "TeamViewRelay: OFF");
    }

    private void saveAndClose() {
        Config config = PlayerProcesses.getConfig();
        config.setServerURL(serverUrl.getValue().trim());
        config.setRoomCode(roomCode.getValue());
        config.setAutoConnectOnMultiplayerJoin(autoConnect.selected());
        config.setUploadEntities(uploadEntities.selected());
        config.save();
        if (PlayerProcesses.isModEnable()) {
            PlayerProcesses.reconnectToServer();
        }
        onClose();
    }
}
