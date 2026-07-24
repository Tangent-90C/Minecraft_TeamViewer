package fun.prof_chen.teamviewer.main_code.config.ui;

public record ConfigUiAction(Type type, ConfigPageId targetPage) {
    public enum Type { STAY, OPEN_PAGE, CLOSE_TO_PARENT }

    public static ConfigUiAction stay() {
        return new ConfigUiAction(Type.STAY, null);
    }

    public static ConfigUiAction open(ConfigPageId page) {
        return new ConfigUiAction(Type.OPEN_PAGE, page);
    }

    public static ConfigUiAction closeToParent() {
        return new ConfigUiAction(Type.CLOSE_TO_PARENT, null);
    }
}
