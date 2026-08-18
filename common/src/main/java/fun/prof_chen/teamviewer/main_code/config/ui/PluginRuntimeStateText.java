package fun.prof_chen.teamviewer.main_code.config.ui;

import fun.prof_chen.teamviewer.main_code.plugin.PluginRuntimeState;

/** Formats generic plugin state values, including an optional observation age. */
final class PluginRuntimeStateText {
    private PluginRuntimeStateText() { }

    static UiText value(PluginRuntimeState state, long nowMillis) {
        if (state.observedAtMillis() == null) return UiText.literal(state.value());
        long ageMillis = Math.max(0L, nowMillis - state.observedAtMillis());
        String age;
        if (ageMillis < 60_000L) age = "刚刚";
        else if (ageMillis < 3_600_000L) age = ageMillis / 60_000L + " 分钟前";
        else if (ageMillis < 86_400_000L) age = ageMillis / 3_600_000L + " 小时前";
        else age = ageMillis / 86_400_000L + " 天前";
        return UiText.literal(state.value().isBlank() ? age : state.value() + " · " + age);
    }
}
