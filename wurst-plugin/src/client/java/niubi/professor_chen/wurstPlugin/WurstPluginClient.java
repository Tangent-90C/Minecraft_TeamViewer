package niubi.professor_chen.wurstPlugin;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import niubi.professor_chen.wurstPlugin.gui.MultiPlayerESPConfigScreen;
import net.wurstclient.WurstClient;
import niubi.professor_chen.wurstPlugin.hook_wurst.MultiPlayerEspHack;
import org.lwjgl.glfw.GLFW;

public class WurstPluginClient implements ClientModInitializer {
    private static KeyBinding multiPlayerESPConfigKey;
    
    @Override
    public void onInitializeClient() {
        // 注册按键绑定
        multiPlayerESPConfigKey = KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding(
                "key.wurst-plugin.multiplayeresp", // 按键绑定的翻译键
                InputUtil.Type.KEYSYM, // 按键类型
                GLFW.GLFW_KEY_O, // 默认按键 O
                "category.wurst-plugin.general" // 按键绑定分类
        ));
        
        // 注册按键事件监听器
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查按键是否被按下
            while (multiPlayerESPConfigKey.wasPressed()) {
                // 打开配置界面
                if (client.player != null) {
                    client.setScreen(new MultiPlayerESPConfigScreen(client.currentScreen));
                }
            }
        });
    }
}