package fun.prof_chen.teamviewer.main_code.config.ui;

/** SDK-facing controller consumed by version-native configuration screens. */
public interface ConfigUiController {
    ConfigPageView page(ConfigPageId pageId, int width, int height);
    void setText(ConfigControlId id, String value);
    void setChecked(ConfigControlId id, boolean checked);
    ConfigUiAction activate(ConfigPageId currentPage, ConfigControlId id);
    ConfigUiAction close(ConfigPageId pageId);
}
