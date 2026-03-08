package fun.prof_chen.teamviewer.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fun.prof_chen.teamviewer.multipleplayeresp.screen.PlayerESPConfigScreen;

public class TeamviewerModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PlayerESPConfigScreen::new;
    }
}
