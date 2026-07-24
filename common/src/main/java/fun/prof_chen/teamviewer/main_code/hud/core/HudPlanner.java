package fun.prof_chen.teamviewer.main_code.hud.core;

import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.config.ui.UiText;
import fun.prof_chen.teamviewer.main_code.hud.model.HudFrame;
import fun.prof_chen.teamviewer.main_code.hud.model.HudLine;
import fun.prof_chen.teamviewer.main_code.hud.model.HudPanel;
import fun.prof_chen.teamviewer.main_code.hud.model.LocalMarkedState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Produces identical HUD content and ordering for every Minecraft adapter. */
public final class HudPlanner {
    private static final int MARGIN = 6;

    public HudFrame plan(Config config, NetworkManager network, boolean enabled, LocalMarkedState markedState) {
        if (config == null || network == null) {
            return HudFrame.empty();
        }
        List<HudPanel> panels = new ArrayList<>();
        int topLeftY = MARGIN;
        if (network.isPacketDumpCaptureActive()) {
            panels.add(new HudPanel("packet-capture", HudPanel.Anchor.TOP_LEFT, MARGIN, topLeftY,
                    5, 3, 12, 0x88AA0000,
                    List.of(new HudLine(UiText.literal("抓包中 REC"), 0xFFFFE082))));
            topLeftY += 22;
        }
        if (config.isShowNetworkTrafficHud()) {
            NetworkManager.TrafficStatsSnapshot stats = network.getTrafficStatsSnapshot();
            UiText upload = UiText.translatable("hud.mc_teamviewer.traffic.up",
                    UiText.literal(formatRate(stats.getUploadApplicationBytesPerSecond())),
                    UiText.literal(formatRate(stats.getUploadWireBytesPerSecond())),
                    UiText.literal(formatSize(stats.getUploadApplicationBytesTotal())),
                    UiText.literal(formatSize(stats.getUploadWireBytesTotal())));
            UiText download = UiText.translatable("hud.mc_teamviewer.traffic.down",
                    UiText.literal(formatRate(stats.getDownloadApplicationBytesPerSecond())),
                    UiText.literal(formatRate(stats.getDownloadWireBytesPerSecond())),
                    UiText.literal(formatSize(stats.getDownloadApplicationBytesTotal())),
                    UiText.literal(formatSize(stats.getDownloadWireBytesTotal())));
            panels.add(new HudPanel("network-traffic", HudPanel.Anchor.TOP_LEFT, MARGIN, topLeftY,
                    4, 2, 10, 0x66000000,
                    List.of(new HudLine(upload, 0xFF7DD3FC), new HudLine(download, 0xFFA7F3D0))));
        }
        if (enabled && markedState != null && markedState.active()) {
            panels.add(new HudPanel("local-marked", HudPanel.Anchor.TOP_RIGHT, MARGIN, MARGIN,
                    4, 3, 12, 0x66000000,
                    List.of(new HudLine(UiText.literal(markedState.indicatorText()), 0xFFFF6B6B))));
        }
        return new HudFrame(panels);
    }

    public static String formatRate(long bytesPerSecond) {
        return formatSize(bytesPerSecond) + "/s";
    }

    public static String formatSize(long bytes) {
        String[] units = {"B", "K", "M", "G"};
        double value = Math.max(0L, bytes);
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return unit == 0 || value >= 100.0
                ? String.format(Locale.ROOT, "%.0f%s", value, units[unit])
                : String.format(Locale.ROOT, "%.1f%s", value, units[unit]);
    }
}
