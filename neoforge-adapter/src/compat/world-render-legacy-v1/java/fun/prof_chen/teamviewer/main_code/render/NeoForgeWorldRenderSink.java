package fun.prof_chen.teamviewer.main_code.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.abstraction.WorldRenderSink;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Immediate-mode renderer for the pre-1.21 rendering API. */
public final class NeoForgeWorldRenderSink implements WorldRenderSink<RenderLevelStageEvent> {
    @Override
    public void render(RenderLevelStageEvent context, WorldRenderFrame frame) {
        if (context == null || frame == null || frame.cameraPosition() == null) return;
        PoseStack poseStack = context.getPoseStack();
        Position3D camera = frame.cameraPosition();
        for (WorldRenderCommand command : frame.commands()) {
            if (command instanceof WorldRenderCommand.Box box) {
                drawBox(poseStack, relative(box.bounds(), camera), box.color(), box.depthTest());
            } else if (command instanceof WorldRenderCommand.Line line) {
                drawLines(poseStack, new float[][]{segment(relative(line.start(), camera),
                        relative(line.end(), camera))}, line.color(), line.depthTest(), 1.0F);
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
        drawLines(stack, new float[][]{
                {x1,y1,z1,x2,y1,z1}, {x2,y1,z1,x2,y1,z2}, {x2,y1,z2,x1,y1,z2}, {x1,y1,z2,x1,y1,z1},
                {x1,y2,z1,x2,y2,z1}, {x2,y2,z1,x2,y2,z2}, {x2,y2,z2,x1,y2,z2}, {x1,y2,z2,x1,y2,z1},
                {x1,y1,z1,x1,y2,z1}, {x2,y1,z1,x2,y2,z1}, {x2,y1,z2,x2,y2,z2}, {x1,y1,z2,x1,y2,z2}
        }, color, depth, 2.5F);
    }

    private static void drawCircle(PoseStack stack, Position3D center, WorldRenderCommand.Circle circle) {
        int count = Math.max(3, circle.segments());
        float[][] lines = new float[count][];
        for (int index = 0; index < count; index++) {
            double first = Math.PI * 2.0 * index / count;
            double second = Math.PI * 2.0 * (index + 1) / count;
            lines[index] = new float[]{
                    (float) (center.x() + Math.cos(first) * circle.radius()), (float) center.y(),
                    (float) (center.z() + Math.sin(first) * circle.radius()),
                    (float) (center.x() + Math.cos(second) * circle.radius()), (float) center.y(),
                    (float) (center.z() + Math.sin(second) * circle.radius())};
        }
        drawLines(stack, lines, circle.color(), circle.depthTest(), 1.0F);
    }

    private static void drawLines(PoseStack stack, float[][] lines, int color, boolean depth, float width) {
        RenderSystem.lineWidth(width);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = stack.last().pose();
        for (float[] line : lines) {
            vertex(buffer, matrix, line[0], line[1], line[2], color);
            vertex(buffer, matrix, line[3], line[4], line[5], color);
        }
        draw(buffer, depth);
    }

    private static void drawBeam(PoseStack stack, Position3D center, double height, double radius,
                                 int color, boolean depth) {
        if (height <= 0 || radius <= 0) return;
        float x1 = (float) (center.x() - radius), x2 = (float) (center.x() + radius);
        float z1 = (float) (center.z() - radius), z2 = (float) (center.z() + radius);
        float y1 = (float) center.y(), y2 = (float) (center.y() + height);
        drawQuads(stack, new float[][]{
                {x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1},
                {x2,y1,z2, x2,y2,z2, x1,y2,z2, x1,y1,z2},
                {x1,y1,z2, x1,y2,z2, x1,y2,z1, x1,y1,z1},
                {x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2}
        }, color, depth);
    }

    private static void drawPlane(PoseStack stack, Position3D center, double halfSize,
                                  int color, boolean depth) {
        if (halfSize <= 0) return;
        float x1 = (float) (center.x() - halfSize), x2 = (float) (center.x() + halfSize);
        float z1 = (float) (center.z() - halfSize), z2 = (float) (center.z() + halfSize);
        float y = (float) center.y();
        drawQuads(stack, new float[][]{{x1,y,z1, x1,y,z2, x2,y,z2, x2,y,z1}}, color, depth);
    }

    private static void drawQuads(PoseStack stack, float[][] quads, int color, boolean depth) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = stack.last().pose();
        for (float[] quad : quads) {
            for (int index = 0; index < 12; index += 3) {
                vertex(buffer, matrix, quad[index], quad[index + 1], quad[index + 2], color);
            }
        }
        draw(buffer, depth);
    }

    private static void draw(BufferBuilder buffer, boolean depth) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (depth) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
                               float x, float y, float z, int color) {
        int normalized = (color >>> 24) == 0 ? color | 0xFF000000 : color;
        buffer.vertex(matrix, x, y, z).color(normalized).endVertex();
    }

    private static float[] segment(Position3D first, Position3D second) {
        return new float[]{(float) first.x(), (float) first.y(), (float) first.z(),
                (float) second.x(), (float) second.y(), (float) second.z()};
    }

    private static Position3D relative(Position3D value, Position3D camera) {
        return new Position3D(value.x() - camera.x(), value.y() - camera.y(), value.z() - camera.z());
    }

    private static AxisAlignedBox3D relative(AxisAlignedBox3D box, Position3D camera) {
        return new AxisAlignedBox3D(box.minX() - camera.x(), box.minY() - camera.y(), box.minZ() - camera.z(),
                box.maxX() - camera.x(), box.maxY() - camera.y(), box.maxZ() - camera.z());
    }
}
