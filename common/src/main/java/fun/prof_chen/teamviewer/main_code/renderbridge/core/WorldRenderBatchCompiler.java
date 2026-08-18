package fun.prof_chen.teamviewer.main_code.renderbridge.core;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderBatch;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts semantic render commands into camera-relative, draw-call-oriented vertex batches. */
public final class WorldRenderBatchCompiler {
    private static final float BOX_LINE_WIDTH = 2.5F;

    private WorldRenderBatchCompiler() { }

    public static WorldRenderBatch compile(WorldRenderFrame frame) {
        if (frame == null || frame.cameraPosition() == null || frame.commands().isEmpty()) {
            return new WorldRenderBatch(List.of(), List.of());
        }
        Position3D camera = frame.cameraPosition();
        Map<LineKey, List<WorldRenderBatch.Vertex>> lines = new LinkedHashMap<>();
        Map<Boolean, List<WorldRenderBatch.Vertex>> quads = new LinkedHashMap<>();
        for (WorldRenderCommand command : frame.commands()) {
            if (command instanceof WorldRenderCommand.Box box) {
                addBox(lines(lines, box.depthTest(), BOX_LINE_WIDTH), box.bounds(), camera, box.color());
            } else if (command instanceof WorldRenderCommand.Line line) {
                List<WorldRenderBatch.Vertex> target = lines(lines, line.depthTest(), line.width());
                add(target, line.start(), camera, line.color());
                add(target, line.end(), camera, line.color());
            } else if (command instanceof WorldRenderCommand.Circle circle) {
                addCircle(lines(lines, circle.depthTest(), 1.0F), circle, camera);
            } else if (command instanceof WorldRenderCommand.VerticalBeam beam) {
                addBeam(quads.computeIfAbsent(beam.depthTest(), ignored -> new ArrayList<>()), beam, camera);
            } else if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
                addPlane(quads.computeIfAbsent(plane.depthTest(), ignored -> new ArrayList<>()), plane, camera);
            }
        }
        List<WorldRenderBatch.LineBatch> lineBatches = lines.entrySet().stream()
                .map(entry -> new WorldRenderBatch.LineBatch(
                        entry.getKey().depthTest(), entry.getKey().width(), entry.getValue()))
                .toList();
        List<WorldRenderBatch.QuadBatch> quadBatches = quads.entrySet().stream()
                .map(entry -> new WorldRenderBatch.QuadBatch(entry.getKey(), entry.getValue()))
                .toList();
        return new WorldRenderBatch(lineBatches, quadBatches);
    }

    private static List<WorldRenderBatch.Vertex> lines(
            Map<LineKey, List<WorldRenderBatch.Vertex>> batches, boolean depthTest, float width) {
        float normalizedWidth = Float.isFinite(width) && width > 0 ? width : 1.0F;
        return batches.computeIfAbsent(new LineKey(depthTest, normalizedWidth), ignored -> new ArrayList<>());
    }

    private static void addBox(List<WorldRenderBatch.Vertex> out, AxisAlignedBox3D box,
                               Position3D camera, int color) {
        Position3D p000 = point(box.minX(), box.minY(), box.minZ());
        Position3D p100 = point(box.maxX(), box.minY(), box.minZ());
        Position3D p101 = point(box.maxX(), box.minY(), box.maxZ());
        Position3D p001 = point(box.minX(), box.minY(), box.maxZ());
        Position3D p010 = point(box.minX(), box.maxY(), box.minZ());
        Position3D p110 = point(box.maxX(), box.maxY(), box.minZ());
        Position3D p111 = point(box.maxX(), box.maxY(), box.maxZ());
        Position3D p011 = point(box.minX(), box.maxY(), box.maxZ());
        edge(out, p000, p100, camera, color); edge(out, p100, p101, camera, color);
        edge(out, p101, p001, camera, color); edge(out, p001, p000, camera, color);
        edge(out, p010, p110, camera, color); edge(out, p110, p111, camera, color);
        edge(out, p111, p011, camera, color); edge(out, p011, p010, camera, color);
        edge(out, p000, p010, camera, color); edge(out, p100, p110, camera, color);
        edge(out, p101, p111, camera, color); edge(out, p001, p011, camera, color);
    }

    private static void addCircle(List<WorldRenderBatch.Vertex> out,
                                  WorldRenderCommand.Circle circle, Position3D camera) {
        int segments = Math.max(3, circle.segments());
        for (int index = 0; index < segments; index++) {
            double first = Math.PI * 2.0 * index / segments;
            double second = Math.PI * 2.0 * (index + 1) / segments;
            edge(out, circlePoint(circle, first), circlePoint(circle, second), camera, circle.color());
        }
    }

    private static void addBeam(List<WorldRenderBatch.Vertex> out,
                                WorldRenderCommand.VerticalBeam beam, Position3D camera) {
        if (beam.height() <= 0 || beam.radius() <= 0) return;
        Position3D center = beam.baseCenter();
        double minX = center.x() - beam.radius(), maxX = center.x() + beam.radius();
        double minZ = center.z() - beam.radius(), maxZ = center.z() + beam.radius();
        double minY = center.y(), maxY = center.y() + beam.height();
        quad(out, point(minX,minY,minZ), point(minX,maxY,minZ), point(maxX,maxY,minZ), point(maxX,minY,minZ), camera, beam.color());
        quad(out, point(maxX,minY,maxZ), point(maxX,maxY,maxZ), point(minX,maxY,maxZ), point(minX,minY,maxZ), camera, beam.color());
        quad(out, point(minX,minY,maxZ), point(minX,maxY,maxZ), point(minX,maxY,minZ), point(minX,minY,minZ), camera, beam.color());
        quad(out, point(maxX,minY,minZ), point(maxX,maxY,minZ), point(maxX,maxY,maxZ), point(maxX,minY,maxZ), camera, beam.color());
    }

    private static void addPlane(List<WorldRenderBatch.Vertex> out,
                                 WorldRenderCommand.HorizontalPlane plane, Position3D camera) {
        if (plane.halfSize() <= 0) return;
        Position3D center = plane.center();
        double minX = center.x() - plane.halfSize(), maxX = center.x() + plane.halfSize();
        double minZ = center.z() - plane.halfSize(), maxZ = center.z() + plane.halfSize();
        quad(out, point(minX,center.y(),minZ), point(minX,center.y(),maxZ),
                point(maxX,center.y(),maxZ), point(maxX,center.y(),minZ), camera, plane.color());
    }

    private static void edge(List<WorldRenderBatch.Vertex> out, Position3D first, Position3D second,
                             Position3D camera, int color) {
        add(out, first, camera, color);
        add(out, second, camera, color);
    }

    private static void quad(List<WorldRenderBatch.Vertex> out, Position3D first, Position3D second,
                             Position3D third, Position3D fourth, Position3D camera, int color) {
        add(out, first, camera, color); add(out, second, camera, color);
        add(out, third, camera, color); add(out, fourth, camera, color);
    }

    private static void add(List<WorldRenderBatch.Vertex> out, Position3D value,
                            Position3D camera, int color) {
        out.add(new WorldRenderBatch.Vertex(
                (float) (value.x() - camera.x()), (float) (value.y() - camera.y()),
                (float) (value.z() - camera.z()), normalizeAlpha(color)));
    }

    private static Position3D circlePoint(WorldRenderCommand.Circle circle, double angle) {
        return point(circle.center().x() + Math.cos(angle) * circle.radius(), circle.center().y(),
                circle.center().z() + Math.sin(angle) * circle.radius());
    }

    private static Position3D point(double x, double y, double z) {
        return new Position3D(x, y, z);
    }

    private static int normalizeAlpha(int color) {
        return (color >>> 24) == 0 ? color | 0xFF000000 : color;
    }

    private record LineKey(boolean depthTest, float width) { }
}
