package fun.prof_chen.teamviewer.client;

import net.fabricmc.api.ClientModInitializer;
import fun.prof_chen.teamviewer.main_code.core.PlayerProcesses;

public class TeamviewerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 初始化客户端功能
        new PlayerProcesses().onInitializeClient();
    }
}