package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Input adapter for the event-object NeoForge screen callbacks. */
public final class PluginManagerScreen extends AbstractPluginManagerScreen {
    public PluginManagerScreen(Screen parent, PluginManagerUiController controller) {
        super(parent, controller);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent input, boolean doubled) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = input.button() == 0 && super.mouseClicked(input, doubled);
        return finishMouseClicked(
                input.x(), input.y(), input.button(), focusedBefore, handledByField);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = super.keyPressed(input);
        return finishKeyPressed(input.key(), focusedBefore, handledByField);
    }

    @Override
    protected void showTooltip(GuiGraphics graphics, Component tooltip, int mouseX, int mouseY) {
        graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);
    }

    @Override
    protected Screen recreate() {
        return new PluginManagerScreen(parent, controller);
    }
}
