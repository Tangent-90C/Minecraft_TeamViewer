package fun.prof_chen.teamviewer.main_code.config.ui;

/** Platform-facing controller for the custom integration-plugin manager. */
public interface PluginManagerUiController {
    PluginManagerView view(int width, int height);

    ConfigUiAction activate(ConfigControlId id);

    void setText(ConfigControlId id, String value);

    void scrollList(int rows);

    void scrollDetail(int pixels);

    void moveSelection(int rows);

    void commitTextSettings();
}
