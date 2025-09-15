package niubi.professor_chen.wurstPlugin.mixin;

import net.wurstclient.event.EventManager;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackList;
import niubi.professor_chen.wurstPlugin.hook_wurst.MultiPlayerEspHack;
import niubi.professor_chen.wurstPlugin.config.MultiPlayerESPConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.TreeMap;

@Mixin(HackList.class)
public class WurstClientMixin {
    @Shadow(remap = false)
    private TreeMap<String, Hack> hax;

    @Shadow(remap = false)
    private EventManager eventManager;


    @Inject(method = "<init>", at = @At(value = "TAIL"), remap = false)
    private void onInitialize(CallbackInfo ci) {
        // 检查是否启用了Wurst Mixin
        // 确保在初始化时正确加载配置
        MultiPlayerESPConfig config = MultiPlayerESPConfig.getInstance();
        if (config.isWurstMixinEnabled()) {
            HackList self = (HackList) (Object) this;
            MultiPlayerEspHack multiPlayerEspHack = new MultiPlayerEspHack();

            Hack hack = (Hack) multiPlayerEspHack;
            hax.put(hack.getName(), hack);

            eventManager.remove(UpdateListener.class, self);
            eventManager.add(UpdateListener.class, self);
        }
    }
    
    // 添加一个方法用于在运行时动态移除hack
    public void removeMultiPlayerEspHack() {
        if (hax.containsKey("MultiPlayerESP")) {
            hax.remove("MultiPlayerESP");
        }
    }

}