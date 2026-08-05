package fun.prof_chen.teamviewer.main_code.render;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;

/** Minecraft 1.21.11 render-state extraction port using vanilla per-frame gizmos. */
public final class NeoForgeWorldRenderSink implements WorldRenderSink<ExtractLevelRenderStateEvent> {
    @Override
    public void render(ExtractLevelRenderStateEvent context, WorldRenderFrame frame) {
        if (context == null || frame == null) return;
        try (Gizmos.TemporaryCollection ignored = context.getLevelRenderer().collectPerFrameGizmos()) {
            for (WorldRenderCommand command : frame.commands()) {
                GizmoProperties properties = create(command);
                if (properties != null && !command.depthTest()) {
                    properties.setAlwaysOnTop();
                }
            }
        }
    }

    private static GizmoProperties create(WorldRenderCommand command) {
        if (command instanceof WorldRenderCommand.Box box) {
            AxisAlignedBox3D bounds = box.bounds();
            return Gizmos.cuboid(new AABB(
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                    GizmoStyle.stroke(box.color(), 2.5F));
        }
        if (command instanceof WorldRenderCommand.Line line) {
            return Gizmos.line(vec(line.start()), vec(line.end()), line.color(), line.width());
        }
        if (command instanceof WorldRenderCommand.VerticalBeam beam) {
            Position3D point = beam.baseCenter();
            return Gizmos.cuboid(new AABB(
                    point.x() - beam.radius(), point.y(), point.z() - beam.radius(),
                    point.x() + beam.radius(), point.y() + beam.height(), point.z() + beam.radius()),
                    GizmoStyle.fill(beam.color()));
        }
        if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
            Position3D point = plane.center();
            return Gizmos.cuboid(new AABB(
                    point.x() - plane.halfSize(), point.y(), point.z() - plane.halfSize(),
                    point.x() + plane.halfSize(), point.y() + 0.02D, point.z() + plane.halfSize()),
                    GizmoStyle.fill(plane.color()));
        }
        if (command instanceof WorldRenderCommand.Circle circle) {
            return Gizmos.circle(vec(circle.center()), (float) circle.radius(),
                    GizmoStyle.stroke(circle.color(), 2.0F));
        }
        return null;
    }

    private static Vec3 vec(Position3D position) {
        return new Vec3(position.x(), position.y(), position.z());
    }
}
