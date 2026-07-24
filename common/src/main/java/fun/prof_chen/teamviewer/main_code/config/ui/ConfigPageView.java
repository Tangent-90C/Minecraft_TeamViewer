package fun.prof_chen.teamviewer.main_code.config.ui;

import java.util.List;

public record ConfigPageView(ConfigPageId pageId, UiText title, int titleY, List<ConfigControlView> controls) {
    public ConfigPageView {
        controls = List.copyOf(controls);
    }
}
