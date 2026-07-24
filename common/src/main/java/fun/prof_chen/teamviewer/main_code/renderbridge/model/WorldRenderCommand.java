package fun.prof_chen.teamviewer.main_code.renderbridge.model;

import fun.prof_chen.teamviewer.main_code.model.Position3D;

public sealed interface WorldRenderCommand permits
        WorldRenderCommand.Box, WorldRenderCommand.Line, WorldRenderCommand.VerticalBeam,
        WorldRenderCommand.HorizontalPlane, WorldRenderCommand.Circle {

    int color();
    boolean depthTest();

    record Box(AxisAlignedBox3D bounds, int color, boolean depthTest) implements WorldRenderCommand { }

    record Line(Position3D start, Position3D end, int color, boolean depthTest, float width)
            implements WorldRenderCommand { }

    record VerticalBeam(Position3D baseCenter, double height, double radius, int color, boolean depthTest)
            implements WorldRenderCommand { }

    record HorizontalPlane(Position3D center, double halfSize, int color, boolean depthTest)
            implements WorldRenderCommand { }

    record Circle(Position3D center, double radius, int segments, int color, boolean depthTest)
            implements WorldRenderCommand { }
}
