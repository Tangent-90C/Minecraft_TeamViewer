package person.professor_chen.teamviewer.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import person.professor_chen.teamviewer.multipleplayeresp.PlayerESPConfigScreen;

public class TeamviewerModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PlayerESPConfigScreen::new;
    }
}
