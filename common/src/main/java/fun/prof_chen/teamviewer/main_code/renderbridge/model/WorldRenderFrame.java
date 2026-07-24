package fun.prof_chen.teamviewer.main_code.renderbridge.model;

import fun.prof_chen.teamviewer.main_code.model.Position3D;

import java.util.List;

/** Commands use absolute world coordinates; the version sink owns camera-space conversion. */
public record WorldRenderFrame(Position3D cameraPosition, List<WorldRenderCommand> commands) {
    public WorldRenderFrame {
        commands = commands == null ? List.of() : List.copyOf(commands);
    }

    public static WorldRenderFrame empty() {
        return new WorldRenderFrame(null, List.of());
    }
}
