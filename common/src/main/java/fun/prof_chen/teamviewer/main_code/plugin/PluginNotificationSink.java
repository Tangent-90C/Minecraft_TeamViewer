package fun.prof_chen.teamviewer.main_code.plugin;

/** Local-only notification surface available to trusted integration plugins. */
@FunctionalInterface
public interface PluginNotificationSink {
    String SERVICE_ID = "teamviewer.notification_sink";

    void showActionBar(String message);
}
