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
import net.minecraft.world.debug.gizmo.BoxGizmo;
import net.minecraft.world.debug.gizmo.CircleGizmo;
import net.minecraft.world.debug.gizmo.Gizmo;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.LineGizmo;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

import java.util.ArrayList;
import java.util.List;

/** Minecraft 1.21.11 render-state extraction port using vanilla debug gizmos. */
public final class FabricWorldRenderSink implements WorldRenderSink<WorldExtractionContext> {
    @Override
    public void render(WorldExtractionContext context, WorldRenderFrame frame) {
        if (context == null || frame == null) {
            return;
        }
        try (GizmoDrawing.CollectorScope ignored = context.worldRenderer().startDrawingGizmos()) {
            List<Gizmo> depthTested = new ArrayList<>();
            List<Gizmo> alwaysOnTop = new ArrayList<>();
            for (WorldRenderCommand command : frame.commands()) {
                Gizmo gizmo = create(command);
                if (gizmo != null) (command.depthTest() ? depthTested : alwaysOnTop).add(gizmo);
            }
            addBatch(depthTested, false);
            addBatch(alwaysOnTop, true);
        }
    }

    private static void addBatch(List<Gizmo> gizmos, boolean alwaysOnTop) {
        if (gizmos.isEmpty()) return;
        VisibilityConfigurable visibility = GizmoDrawing.collect(new BatchGizmo(List.copyOf(gizmos)));
        if (alwaysOnTop) visibility.ignoreOcclusion();
    }

    private static Gizmo create(WorldRenderCommand command) {
        if (command instanceof WorldRenderCommand.Box box) {
            AxisAlignedBox3D bounds = box.bounds();
            return new BoxGizmo(new Box(
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                    DrawStyle.stroked(box.color(), 2.5F), false);
        }
        if (command instanceof WorldRenderCommand.Line line) {
            return new LineGizmo(vec(line.start()), vec(line.end()), line.color(), line.width());
        }
        if (command instanceof WorldRenderCommand.VerticalBeam beam) {
            Position3D point = beam.baseCenter();
            return new BoxGizmo(new Box(
                    point.x() - beam.radius(), point.y(), point.z() - beam.radius(),
                    point.x() + beam.radius(), point.y() + beam.height(), point.z() + beam.radius()),
                    DrawStyle.filled(beam.color()), false);
        }
        if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
            Position3D point = plane.center();
            return new BoxGizmo(new Box(
                    point.x() - plane.halfSize(), point.y(), point.z() - plane.halfSize(),
                    point.x() + plane.halfSize(), point.y() + 0.02D, point.z() + plane.halfSize()),
                    DrawStyle.filled(plane.color()), false);
        }
        if (command instanceof WorldRenderCommand.Circle circle) {
            return new CircleGizmo(
                    vec(circle.center()), (float) circle.radius(), DrawStyle.stroked(circle.color(), 2.0F));
        }
        return null;
    }

    private static Vec3d vec(Position3D position) {
        return new Vec3d(position.x(), position.y(), position.z());
    }

    private record BatchGizmo(List<Gizmo> gizmos) implements Gizmo {
        @Override
        public void draw(net.minecraft.world.debug.gizmo.GizmoDrawer drawer, float alpha) {
            for (Gizmo gizmo : gizmos) gizmo.draw(drawer, alpha);
        }
    }
}
