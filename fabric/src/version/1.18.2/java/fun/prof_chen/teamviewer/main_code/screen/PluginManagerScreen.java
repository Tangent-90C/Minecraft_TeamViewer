package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import net.minecraft.client.gui.screen.Screen;

/** Fabric 1.18 close-callback adapter for the shared MatrixStack screen. */
public final class PluginManagerScreen extends AbstractPluginManagerScreen {
    public PluginManagerScreen(Screen parent, PluginManagerUiController controller) {
        super(parent, controller);
    }

    @Override
    protected Screen recreate() {
        return new PluginManagerScreen(parent, controller);
    }

    public void close() {
        closeToParent();
    }

    public void onClose() {
        close();
    }
}
