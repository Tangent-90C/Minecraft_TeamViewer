package fun.prof_chen.teamviewer.main_code.renderbridge.abstraction;

import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;

/**
 * Version-specific executor for common world-render commands. C is the platform render context
 * and must never escape back into common business logic.
 */
public interface WorldRenderSink<C> {
    void render(C context, WorldRenderFrame frame);
}
