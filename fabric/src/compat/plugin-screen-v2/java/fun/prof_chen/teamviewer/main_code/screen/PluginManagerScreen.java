package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import net.minecraft.client.gui.screen.Screen;

/** Input adapter for the classic DrawContext screen callbacks. */
public final class PluginManagerScreen extends AbstractPluginManagerScreen {
    public PluginManagerScreen(Screen parent, PluginManagerUiController controller) {
        super(parent, controller);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = button == 0 && super.mouseClicked(mouseX, mouseY, button);
        return finishMouseClicked(mouseX, mouseY, button, focusedBefore, handledByField);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        return scroll(mouseX, mouseY, verticalAmount);
    }

    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return scroll(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = super.keyPressed(keyCode, scanCode, modifiers);
        return finishKeyPressed(keyCode, focusedBefore, handledByField);
    }

    @Override
    protected Screen recreate() {
        return new PluginManagerScreen(parent, controller);
    }
}
