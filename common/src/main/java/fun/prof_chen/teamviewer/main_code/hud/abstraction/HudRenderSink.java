package fun.prof_chen.teamviewer.main_code.hud.abstraction;

import fun.prof_chen.teamviewer.main_code.hud.model.HudFrame;

public interface HudRenderSink<C> {
    void render(C context, HudFrame frame);
}
