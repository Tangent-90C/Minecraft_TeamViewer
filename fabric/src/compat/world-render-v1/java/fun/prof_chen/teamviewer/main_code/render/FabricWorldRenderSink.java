package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.bridge.UnifiedRenderModule;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.RenderBridge;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.RenderContextHandle;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

public final class FabricWorldRenderSink implements WorldRenderSink<WorldRenderContext> {
    private final RenderBridge render = new UnifiedRenderModule();

    @Override
    public void render(WorldRenderContext context, WorldRenderFrame frame) {
        if (context == null || context.matrixStack() == null || frame == null || frame.cameraPosition() == null) return;
        RenderContextHandle handle = RenderContextHandle.of(context.matrixStack());
        Position3D camera = frame.cameraPosition();
        for (WorldRenderCommand command : frame.commands()) {
            if (command instanceof WorldRenderCommand.Box box) {
                render.drawOutlinedBox(handle, relative(box.bounds(), camera), box.color(), box.depthTest());
            } else if (command instanceof WorldRenderCommand.Line line) {
                render.drawLine(handle, relative(line.start(), camera), relative(line.end(), camera), line.color(), line.depthTest());
            } else if (command instanceof WorldRenderCommand.VerticalBeam beam) {
                render.drawVerticalBeam(handle, relative(beam.baseCenter(), camera), beam.height(), beam.radius(), beam.color(), beam.depthTest());
            } else if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
                render.drawHorizontalPlane(handle, relative(plane.center(), camera), plane.halfSize(), plane.color(), plane.depthTest());
            } else if (command instanceof WorldRenderCommand.Circle circle) {
                drawCircle(handle, relative(circle.center(), camera), circle);
            }
        }
    }

    private void drawCircle(RenderContextHandle handle, Position3D center, WorldRenderCommand.Circle circle) {
        Position3D previous = null;
        int segments = Math.max(3, circle.segments());
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            Position3D current = new Position3D(center.x() + Math.cos(angle) * circle.radius(), center.y(),
                    center.z() + Math.sin(angle) * circle.radius());
            if (previous != null) render.drawLine(handle, previous, current, circle.color(), circle.depthTest());
            previous = current;
        }
    }

    private static Position3D relative(Position3D value, Position3D camera) {
        return new Position3D(value.x() - camera.x(), value.y() - camera.y(), value.z() - camera.z());
    }

    private static AxisAlignedBox3D relative(AxisAlignedBox3D box, Position3D camera) {
        return new AxisAlignedBox3D(box.minX() - camera.x(), box.minY() - camera.y(), box.minZ() - camera.z(),
                box.maxX() - camera.x(), box.maxY() - camera.y(), box.maxZ() - camera.z());
    }
}
