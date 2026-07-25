package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class NeoForgeWorldRenderSink implements WorldRenderSink<RenderLevelStageEvent> {
    @Override
    public void render(RenderLevelStageEvent ignored, WorldRenderFrame frame) {
        if (frame == null) return;
        for (WorldRenderCommand command : frame.commands()) {
            GizmoProperties properties;
            if (command instanceof WorldRenderCommand.Box box) {
                AxisAlignedBox3D b = box.bounds();
                properties = Gizmos.cuboid(new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()),
                        GizmoStyle.stroke(box.color(), 2.5F));
            } else if (command instanceof WorldRenderCommand.Line line) {
                properties = Gizmos.line(vec(line.start()), vec(line.end()), line.color(), line.width());
            } else if (command instanceof WorldRenderCommand.VerticalBeam beam) {
                Position3D p = beam.baseCenter();
                properties = Gizmos.cuboid(new AABB(p.x() - beam.radius(), p.y(), p.z() - beam.radius(),
                                p.x() + beam.radius(), p.y() + beam.height(), p.z() + beam.radius()),
                        GizmoStyle.fill(beam.color()));
            } else if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
                Position3D p = plane.center();
                properties = Gizmos.cuboid(new AABB(p.x() - plane.halfSize(), p.y(), p.z() - plane.halfSize(),
                                p.x() + plane.halfSize(), p.y() + 0.02, p.z() + plane.halfSize()),
                        GizmoStyle.fill(plane.color()));
            } else if (command instanceof WorldRenderCommand.Circle circle) {
                properties = Gizmos.circle(vec(circle.center()), (float) circle.radius(), GizmoStyle.stroke(circle.color(), 2.0F));
            } else {
                continue;
            }
            if (!command.depthTest()) properties.setAlwaysOnTop();
        }
    }

    private static Vec3 vec(Position3D position) {
        return new Vec3(position.x(), position.y(), position.z());
    }
}
