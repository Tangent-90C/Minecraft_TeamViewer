package fun.prof_chen.teamviewer.main_code.config.ui;

import java.util.Objects;

/** Independent configuration and plugin-manager controllers for one native UI session. */
public record ClientUiSession(
        ConfigUiController config,
        PluginManagerUiController plugins) {
    public ClientUiSession {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(plugins, "plugins");
    }
}
