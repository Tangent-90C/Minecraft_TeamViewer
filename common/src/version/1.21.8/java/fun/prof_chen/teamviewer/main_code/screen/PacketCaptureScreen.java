package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.PlayerProcesses;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class PacketCaptureScreen extends Screen {
    private static final int COMPONENT_WIDTH = 240;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int BUTTON_SPACING = 28;
    private static final int TEXT_SPACING = 18;
    private static final int MAX_TOOLTIP_CHARS = 52;

    private final Screen parent;
    private ButtonWidget startButton;
    private ButtonWidget stopButton;
    private ButtonWidget backButton;
    private int startY;

    public PacketCaptureScreen(Screen parent) {
        super(Text.translatable("screen.mc_teamviewer.packet_capture.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int totalHeight = TEXT_SPACING * 5 + BUTTON_SPACING * 3;
        startY = (this.height - totalHeight) / 2;
        int x = (this.width - COMPONENT_WIDTH) / 2;

        int buttonY = startY + TEXT_SPACING * 4;
        this.startButton = ButtonWidget.builder(
                Text.translatable("screen.mc_teamviewer.packet_capture.start"),
                button -> startCapture()
        ).dimensions(x, buttonY, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.startButton);

        this.stopButton = ButtonWidget.builder(
                Text.translatable("screen.mc_teamviewer.packet_capture.stop"),
                button -> stopCapture()
        ).dimensions(x, buttonY + BUTTON_SPACING, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.stopButton);

        this.backButton = ButtonWidget.builder(
                Text.translatable("screen.mc_teamviewer.config.back"),
                button -> close()
        ).dimensions(x, buttonY + BUTTON_SPACING * 2, COMPONENT_WIDTH, COMPONENT_HEIGHT).build();
        this.addDrawableChild(this.backButton);

        updateButtons();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, startY - 24, 0xFFFFFF);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.mc_teamviewer.packet_capture.description"),
                centerX,
                startY,
                0xE0E0E0
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                buildStatusText(),
                centerX,
                startY + TEXT_SPACING,
                0xFFD166
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                buildCurrentPathText(),
                centerX,
                startY + TEXT_SPACING * 2,
                0xA7F3D0
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                buildLastSavedPathText(),
                centerX,
                startY + TEXT_SPACING * 3,
                0x93C5FD
        );

        if (isMouseOverPathLine(mouseY, startY + TEXT_SPACING * 2)) {
            drawPathTooltip(context, mouseX, mouseY, PlayerProcesses.getNetworkManager().getPacketDumpCurrentPath());
            return;
        }
        if (isMouseOverPathLine(mouseY, startY + TEXT_SPACING * 3)) {
            drawPathTooltip(context, mouseX, mouseY, PlayerProcesses.getNetworkManager().getPacketDumpLastSavedPath());
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private void startCapture() {
        NetworkManager networkManager = PlayerProcesses.getNetworkManager();
        networkManager.startPacketDumpCapture();
        notifyPlayer("§c[TV] 已开始抓包，游戏内将持续显示抓包提示");
        updateButtons();
    }

    private void stopCapture() {
        NetworkManager networkManager = PlayerProcesses.getNetworkManager();
        networkManager.stopPacketDumpCapture();
        notifyPlayer("§a[TV] 已结束抓包");
        updateButtons();
    }

    private void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        client.player.sendMessage(Text.literal(message), true);
    }

    private void updateButtons() {
        boolean active = PlayerProcesses.getNetworkManager().isPacketDumpCaptureActive();
        if (startButton != null) {
            startButton.active = !active;
        }
        if (stopButton != null) {
            stopButton.active = active;
        }
    }

    private Text buildStatusText() {
        NetworkManager networkManager = PlayerProcesses.getNetworkManager();
        if (!networkManager.isPacketDumpCaptureActive()) {
            return Text.translatable("screen.mc_teamviewer.packet_capture.status_idle");
        }
        if (networkManager.getPacketDumpCurrentPath() == null || networkManager.getPacketDumpCurrentPath().isBlank()) {
            return Text.translatable("screen.mc_teamviewer.packet_capture.status_waiting");
        }
        return Text.translatable("screen.mc_teamviewer.packet_capture.status_running");
    }

    private Text buildCurrentPathText() {
        String path = PlayerProcesses.getNetworkManager().getPacketDumpCurrentPath();
        if (path == null || path.isBlank()) {
            return Text.translatable("screen.mc_teamviewer.packet_capture.current_file_empty");
        }
        return Text.translatable("screen.mc_teamviewer.packet_capture.current_file", abbreviatePath(path));
    }

    private Text buildLastSavedPathText() {
        String path = PlayerProcesses.getNetworkManager().getPacketDumpLastSavedPath();
        if (path == null || path.isBlank()) {
            return Text.translatable("screen.mc_teamviewer.packet_capture.last_file_empty");
        }
        return Text.translatable("screen.mc_teamviewer.packet_capture.last_file", abbreviatePath(path));
    }

    private boolean isMouseOverPathLine(int mouseY, int lineY) {
        return mouseY >= lineY - 2 && mouseY <= lineY + 10;
    }

    private void drawPathTooltip(DrawContext context, int mouseX, int mouseY, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        context.drawTooltip(this.textRenderer, splitTooltipLines(path, MAX_TOOLTIP_CHARS), mouseX, mouseY);
    }

    private List<Text> splitTooltipLines(String input, int maxChars) {
        List<Text> lines = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return lines;
        }
        int start = 0;
        while (start < input.length()) {
            int end = Math.min(start + maxChars, input.length());
            lines.add(Text.of(input.substring(start, end)));
            start = end;
        }
        return lines;
    }

    private String abbreviatePath(String path) {
        if (path == null || path.length() <= 36) {
            return path == null ? "" : path;
        }
        return "..." + path.substring(path.length() - 33);
    }
}
