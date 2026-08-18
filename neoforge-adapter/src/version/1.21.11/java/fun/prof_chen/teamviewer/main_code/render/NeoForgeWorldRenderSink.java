package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.minecraft.gizmos.CircleGizmo;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.LineGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;

import java.util.ArrayList;
import java.util.List;

/** Minecraft 1.21.11 render-state extraction port using vanilla per-frame gizmos. */
public final class NeoForgeWorldRenderSink implements WorldRenderSink<ExtractLevelRenderStateEvent> {
    @Override
    public void render(ExtractLevelRenderStateEvent context, WorldRenderFrame frame) {
        if (context == null || frame == null) return;
        try (Gizmos.TemporaryCollection ignored = context.getLevelRenderer().collectPerFrameGizmos()) {
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
        GizmoProperties properties = Gizmos.addGizmo(new BatchGizmo(List.copyOf(gizmos)));
        if (alwaysOnTop) properties.setAlwaysOnTop();
    }

    private static Gizmo create(WorldRenderCommand command) {
        if (command instanceof WorldRenderCommand.Box box) {
            AxisAlignedBox3D bounds = box.bounds();
            return new CuboidGizmo(new AABB(
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                    GizmoStyle.stroke(box.color(), 2.5F), false);
        }
        if (command instanceof WorldRenderCommand.Line line) {
            return new LineGizmo(vec(line.start()), vec(line.end()), line.color(), line.width());
        }
        if (command instanceof WorldRenderCommand.VerticalBeam beam) {
            Position3D point = beam.baseCenter();
            return new CuboidGizmo(new AABB(
                    point.x() - beam.radius(), point.y(), point.z() - beam.radius(),
                    point.x() + beam.radius(), point.y() + beam.height(), point.z() + beam.radius()),
                    GizmoStyle.fill(beam.color()), false);
        }
        if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
            Position3D point = plane.center();
            return new CuboidGizmo(new AABB(
                    point.x() - plane.halfSize(), point.y(), point.z() - plane.halfSize(),
                    point.x() + plane.halfSize(), point.y() + 0.02D, point.z() + plane.halfSize()),
                    GizmoStyle.fill(plane.color()), false);
        }
        if (command instanceof WorldRenderCommand.Circle circle) {
            return new CircleGizmo(vec(circle.center()), (float) circle.radius(),
                    GizmoStyle.stroke(circle.color(), 2.0F));
        }
        return null;
    }

    private static Vec3 vec(Position3D position) {
        return new Vec3(position.x(), position.y(), position.z());
    }

    private record BatchGizmo(List<Gizmo> gizmos) implements Gizmo {
        @Override
        public void emit(net.minecraft.gizmos.GizmoPrimitives primitives, float alpha) {
            for (Gizmo gizmo : gizmos) gizmo.emit(primitives, alpha);
        }
    }
}
