package person.professor_chen.teamviewer.client;

import net.fabricmc.api.ClientModInitializer;
import person.professor_chen.teamviewer.multipleplayeresp.core.StandaloneMultiPlayerESP;

public class TeamviewerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 初始化客户端功能
        new StandaloneMultiPlayerESP().onInitializeClient();
    }
}