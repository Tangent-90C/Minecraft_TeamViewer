package fun.prof_chen.teamviewer.main_code.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Executes immutable common world commands using the 1.21.8 NeoForge render event. */
public final class NeoForgeWorldRenderSink implements WorldRenderSink<RenderLevelStageEvent> {
    private static final RenderType NO_DEPTH_LINES = createNoDepthLines();
    private static final RenderType NO_DEPTH_QUADS = createNoDepthQuads();

    @Override
    public void render(RenderLevelStageEvent context, WorldRenderFrame frame) {
        if (context == null || frame == null || frame.cameraPosition() == null) return;
        PoseStack poseStack = context.getPoseStack();
        Position3D camera = frame.cameraPosition();
        for (WorldRenderCommand command : frame.commands()) {
            if (command instanceof WorldRenderCommand.Box box) {
                drawBox(poseStack, relative(box.bounds(), camera), box.color(), box.depthTest());
            } else if (command instanceof WorldRenderCommand.Line line) {
                drawLine(poseStack, relative(line.start(), camera), relative(line.end(), camera),
                        line.color(), line.depthTest());
            } else if (command instanceof WorldRenderCommand.VerticalBeam beam) {
                drawBeam(poseStack, relative(beam.baseCenter(), camera), beam.height(), beam.radius(),
                        beam.color(), beam.depthTest());
            } else if (command instanceof WorldRenderCommand.HorizontalPlane plane) {
                drawPlane(poseStack, relative(plane.center(), camera), plane.halfSize(),
                        plane.color(), plane.depthTest());
            } else if (command instanceof WorldRenderCommand.Circle circle) {
                drawCircle(poseStack, relative(circle.center(), camera), circle);
            }
        }
    }

    private static void drawBox(PoseStack stack, AxisAlignedBox3D box, int color, boolean depth) {
        float x1 = (float) box.minX(), y1 = (float) box.minY(), z1 = (float) box.minZ();
        float x2 = (float) box.maxX(), y2 = (float) box.maxY(), z2 = (float) box.maxZ();
        float[][] edges = {
                {x1,y1,z1,x2,y1,z1}, {x2,y1,z1,x2,y1,z2}, {x2,y1,z2,x1,y1,z2}, {x1,y1,z2,x1,y1,z1},
                {x1,y2,z1,x2,y2,z1}, {x2,y2,z1,x2,y2,z2}, {x2,y2,z2,x1,y2,z2}, {x1,y2,z2,x1,y2,z1},
                {x1,y1,z1,x1,y2,z1}, {x2,y1,z1,x2,y2,z1}, {x2,y1,z2,x2,y2,z2}, {x1,y1,z2,x1,y2,z2}
        };
        drawSegments(stack, edges, color, depth, 2.5F);
    }

    private static void drawLine(PoseStack stack, Position3D start, Position3D end, int color, boolean depth) {
        drawSegments(stack, new float[][]{{(float) start.x(), (float) start.y(), (float) start.z(),
                (float) end.x(), (float) end.y(), (float) end.z()}}, color, depth, 1.0F);
    }

    private static void drawCircle(PoseStack stack, Position3D center, WorldRenderCommand.Circle circle) {
        int segments = Math.max(3, circle.segments());
        float[][] lines = new float[segments][];
        for (int i = 0; i < segments; i++) {
            double first = Math.PI * 2.0 * i / segments;
            double second = Math.PI * 2.0 * (i + 1) / segments;
            lines[i] = new float[]{
                    (float) (center.x() + Math.cos(first) * circle.radius()), (float) center.y(),
                    (float) (center.z() + Math.sin(first) * circle.radius()),
                    (float) (center.x() + Math.cos(second) * circle.radius()), (float) center.y(),
                    (float) (center.z() + Math.sin(second) * circle.radius())};
        }
        drawSegments(stack, lines, circle.color(), circle.depthTest(), 1.0F);
    }

    private static void drawSegments(PoseStack stack, float[][] edges, int color, boolean depth, float width) {
        RenderSystem.lineWidth(width);
        Matrix4f matrix = stack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (float[] edge : edges) {
            buffer.addVertex(matrix, edge[0], edge[1], edge[2]).setColor(normalizeAlpha(color));
            buffer.addVertex(matrix, edge[3], edge[4], edge[5]).setColor(normalizeAlpha(color));
        }
        (depth ? RenderType.debugLineStrip(width) : NO_DEPTH_LINES).draw(buffer.buildOrThrow());
    }

    private static void drawBeam(PoseStack stack, Position3D center, double height, double radius,
                                 int color, boolean depth) {
        if (height <= 0 || radius <= 0) return;
        float minX = (float) (center.x() - radius), maxX = (float) (center.x() + radius);
        float minZ = (float) (center.z() - radius), maxZ = (float) (center.z() + radius);
        float minY = (float) center.y(), maxY = (float) (center.y() + height);
        float[][] quads = {
                {minX,minY,minZ, minX,maxY,minZ, maxX,maxY,minZ, maxX,minY,minZ},
                {maxX,minY,maxZ, maxX,maxY,maxZ, minX,maxY,maxZ, minX,minY,maxZ},
                {minX,minY,maxZ, minX,maxY,maxZ, minX,maxY,minZ, minX,minY,minZ},
                {maxX,minY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ, maxX,minY,maxZ}
        };
        drawQuads(stack, quads, color, depth);
    }

    private static void drawPlane(PoseStack stack, Position3D center, double halfSize, int color, boolean depth) {
        if (halfSize <= 0) return;
        float y = (float) center.y();
        float minX = (float) (center.x() - halfSize), maxX = (float) (center.x() + halfSize);
        float minZ = (float) (center.z() - halfSize), maxZ = (float) (center.z() + halfSize);
        drawQuads(stack, new float[][]{{minX,y,minZ, minX,y,maxZ, maxX,y,maxZ, maxX,y,minZ}}, color, depth);
    }

    private static void drawQuads(PoseStack stack, float[][] quads, int color, boolean depth) {
        Matrix4f matrix = stack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] quad : quads) {
            for (int index = 0; index < 12; index += 3) {
                buffer.addVertex(matrix, quad[index], quad[index + 1], quad[index + 2]).setColor(normalizeAlpha(color));
            }
        }
        (depth ? RenderType.debugQuads() : NO_DEPTH_QUADS).draw(buffer.buildOrThrow());
    }

    private static RenderType createNoDepthLines() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false);
        return RenderType.create("teamviewer_no_depth_lines", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.DEBUG_LINE_STRIP, 1536, false, false, state);
    }

    private static RenderType createNoDepthQuads() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false);
        return RenderType.create("teamviewer_no_depth_quads", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1536, false, true, state);
    }

    private static int normalizeAlpha(int color) {
        return (color >>> 24) == 0 ? color | 0xFF000000 : color;
    }

    private static Position3D relative(Position3D value, Position3D camera) {
        return new Position3D(value.x() - camera.x(), value.y() - camera.y(), value.z() - camera.z());
    }

    private static AxisAlignedBox3D relative(AxisAlignedBox3D box, Position3D camera) {
        return new AxisAlignedBox3D(box.minX() - camera.x(), box.minY() - camera.y(), box.minZ() - camera.z(),
                box.maxX() - camera.x(), box.maxY() - camera.y(), box.maxZ() - camera.z());
    }
}
