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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class NeoForgeWorldRenderSink implements WorldRenderSink<RenderLevelStageEvent> {
    @Override
    public void render(RenderLevelStageEvent ignored, WorldRenderFrame frame) {
        if (frame == null || frame.commands().isEmpty()) return;
        List<Gizmo> depthTested = new ArrayList<>();
        List<Gizmo> alwaysOnTop = new ArrayList<>();
        for (WorldRenderCommand command : frame.commands()) {
            Gizmo gizmo = create(command);
            if (gizmo != null) (command.depthTest() ? depthTested : alwaysOnTop).add(gizmo);
        }
        addBatch(depthTested, false);
        addBatch(alwaysOnTop, true);
    }

    private static void addBatch(List<Gizmo> gizmos, boolean alwaysOnTop) {
        if (gizmos.isEmpty()) return;
        GizmoProperties properties = Gizmos.addGizmo(new BatchGizmo(List.copyOf(gizmos)));
        if (alwaysOnTop) properties.setAlwaysOnTop();
    }

    private static Gizmo create(WorldRenderCommand command) {
        if (command instanceof WorldRenderCommand.Box box) {
            AxisAlignedBox3D b = box.bounds();
            return new CuboidGizmo(new AABB(
                    b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()),
                    GizmoStyle.stroke(box.color(), 2.5F), false);
        }
        if (command instanceof WorldRenderCommand.Line line) {
            return new LineGizmo(vec(line.start()), vec(line.end()), line.color(), line.width());
        }
        if (command instanceof WorldRenderCommand.VerticalBeam beam) {
            Position3D p = beam.baseCenter();
            return new CuboidGizmo(new AABB(
                    p.x() - beam.radius(), p.y(), p.z() - beam.radius(),
                    p.x() + beam.radius(), p.y() + beam.height(), p.z() + beam.radius()),
                    GizmoStyle.fill(beam.color()), false);
        }
        if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
            Position3D p = plane.center();
            return new CuboidGizmo(new AABB(
                    p.x() - plane.halfSize(), p.y(), p.z() - plane.halfSize(),
                    p.x() + plane.halfSize(), p.y() + 0.02, p.z() + plane.halfSize()),
                    GizmoStyle.fill(plane.color()), false);
        }
        if (command instanceof WorldRenderCommand.Circle circle) {
            return new CircleGizmo(
                    vec(circle.center()), (float) circle.radius(), GizmoStyle.stroke(circle.color(), 2.0F));
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
