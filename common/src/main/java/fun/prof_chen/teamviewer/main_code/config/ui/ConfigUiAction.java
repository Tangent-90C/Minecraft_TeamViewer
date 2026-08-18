package fun.prof_chen.teamviewer.main_code.config.ui;

public record ConfigUiAction(Type type, ConfigPageId targetPage) {
    public enum Type { STAY, RELOAD_PAGE, OPEN_PAGE, OPEN_PLUGIN_MANAGER, CLOSE_TO_PARENT }

    public static ConfigUiAction stay() {
        return new ConfigUiAction(Type.STAY, null);
    }

    public static ConfigUiAction open(ConfigPageId page) {
        return new ConfigUiAction(Type.OPEN_PAGE, page);
    }

    public static ConfigUiAction reload() {
        return new ConfigUiAction(Type.RELOAD_PAGE, null);
    }

    public static ConfigUiAction openPluginManager() {
        return new ConfigUiAction(Type.OPEN_PLUGIN_MANAGER, null);
    }

    public static ConfigUiAction closeToParent() {
        return new ConfigUiAction(Type.CLOSE_TO_PARENT, null);
    }
}
