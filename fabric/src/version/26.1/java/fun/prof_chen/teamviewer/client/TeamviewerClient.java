package fun.prof_chen.teamviewer.client;

import fun.prof_chen.teamviewer.main_code.core.PlayerProcesses;
import net.fabricmc.api.ClientModInitializer;

public final class TeamviewerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new PlayerProcesses().onInitializeClient();
    }
}
