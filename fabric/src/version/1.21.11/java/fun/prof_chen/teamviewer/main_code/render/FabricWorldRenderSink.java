package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

/** Minecraft 1.21.11 render-state extraction port using vanilla debug gizmos. */
public final class FabricWorldRenderSink implements WorldRenderSink<WorldExtractionContext> {
    @Override
    public void render(WorldExtractionContext context, WorldRenderFrame frame) {
        if (context == null || frame == null) {
            return;
        }
        try (GizmoDrawing.CollectorScope ignored = context.worldRenderer().startDrawingGizmos()) {
            for (WorldRenderCommand command : frame.commands()) {
                VisibilityConfigurable gizmo = create(command);
                if (gizmo != null && !command.depthTest()) {
                    gizmo.ignoreOcclusion();
                }
            }
        }
    }

    private static VisibilityConfigurable create(WorldRenderCommand command) {
        if (command instanceof WorldRenderCommand.Box box) {
            AxisAlignedBox3D bounds = box.bounds();
            return GizmoDrawing.box(new Box(
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                    DrawStyle.stroked(box.color(), 2.5F));
        }
        if (command instanceof WorldRenderCommand.Line line) {
            return GizmoDrawing.line(vec(line.start()), vec(line.end()), line.color(), line.width());
        }
        if (command instanceof WorldRenderCommand.VerticalBeam beam) {
            Position3D point = beam.baseCenter();
            return GizmoDrawing.box(new Box(
                    point.x() - beam.radius(), point.y(), point.z() - beam.radius(),
                    point.x() + beam.radius(), point.y() + beam.height(), point.z() + beam.radius()),
                    DrawStyle.filled(beam.color()));
        }
        if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
            Position3D point = plane.center();
            return GizmoDrawing.box(new Box(
                    point.x() - plane.halfSize(), point.y(), point.z() - plane.halfSize(),
                    point.x() + plane.halfSize(), point.y() + 0.02D, point.z() + plane.halfSize()),
                    DrawStyle.filled(plane.color()));
        }
        if (command instanceof WorldRenderCommand.Circle circle) {
            return GizmoDrawing.circle(
                    vec(circle.center()), (float) circle.radius(), DrawStyle.stroked(circle.color(), 2.0F));
        }
        return null;
    }

    private static Vec3d vec(Position3D position) {
        return new Vec3d(position.x(), position.y(), position.z());
    }
}
