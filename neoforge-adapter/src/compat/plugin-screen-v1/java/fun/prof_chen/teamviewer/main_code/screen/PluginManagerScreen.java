package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Input adapter for the classic NeoForge screen callbacks. */
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = super.keyPressed(keyCode, scanCode, modifiers);
        return finishKeyPressed(keyCode, focusedBefore, handledByField);
    }

    @Override
    protected void showTooltip(GuiGraphics graphics, Component tooltip, int mouseX, int mouseY) {
        PluginManagerTooltipCompat.show(graphics, font, tooltip, mouseX, mouseY);
    }

    @Override
    protected Screen recreate() {
        return new PluginManagerScreen(parent, controller);
    }
}
