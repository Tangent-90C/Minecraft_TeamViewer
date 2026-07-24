package fun.prof_chen.teamviewer.main_code.hud.model;

import java.util.List;

public record HudFrame(List<HudPanel> panels) {
    public HudFrame {
        panels = panels == null ? List.of() : List.copyOf(panels);
    }

    public static HudFrame empty() {
        return new HudFrame(List.of());
    }
}
