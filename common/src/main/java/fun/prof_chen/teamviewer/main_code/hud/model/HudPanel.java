package fun.prof_chen.teamviewer.main_code.hud.model;

import java.util.List;

public record HudPanel(String id, Anchor anchor, int xMargin, int y, int paddingX, int paddingY,
                       int lineHeight, int backgroundColor, List<HudLine> lines) {
    public enum Anchor { TOP_LEFT, TOP_RIGHT }

    public HudPanel {
        lines = List.copyOf(lines);
    }
}
