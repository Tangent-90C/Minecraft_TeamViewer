package fun.prof_chen.teamviewer.main_code.screen;

import fun.prof_chen.teamviewer.main_code.config.ui.ConfigControlId;
import fun.prof_chen.teamviewer.main_code.config.ui.PluginManagerUiController;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;

/** Input adapter for the event-object DrawContext screen callbacks. */
public final class PluginManagerScreen extends AbstractPluginManagerScreen {
    public PluginManagerScreen(Screen parent, PluginManagerUiController controller) {
        super(parent, controller);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = click.button() == 0 && super.mouseClicked(click, doubled);
        return finishMouseClicked(
                click.x(), click.y(), click.button(), focusedBefore, handledByField);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return scroll(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        ConfigControlId focusedBefore = focusedField();
        boolean handledByField = super.keyPressed(input);
        return finishKeyPressed(input.key(), focusedBefore, handledByField);
    }

    @Override
    protected Screen recreate() {
        return new PluginManagerScreen(parent, controller);
    }
}
