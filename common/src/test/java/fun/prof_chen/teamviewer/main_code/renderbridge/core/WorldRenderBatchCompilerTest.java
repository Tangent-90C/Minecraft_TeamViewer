package fun.prof_chen.teamviewer.main_code.renderbridge.core;

import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.AxisAlignedBox3D;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderBatch;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldRenderBatchCompilerTest {
    @Test
    void groupsLinesAndCompilesGeometryRelativeToCamera() {
        WorldRenderFrame frame = new WorldRenderFrame(new Position3D(10, 20, 30), List.of(
                new WorldRenderCommand.Box(new AxisAlignedBox3D(10, 20, 30, 11, 22, 31), 0x112233, true),
                new WorldRenderCommand.Line(new Position3D(10, 20, 30), new Position3D(12, 23, 34),
                        0x80112233, false, 1.0F),
                new WorldRenderCommand.Circle(new Position3D(10, 20, 30), 2, 4, 0xFF445566, false)));

        WorldRenderBatch batch = WorldRenderBatchCompiler.compile(frame);

        assertEquals(2, batch.lines().size());
        assertEquals(24, batch.lines().get(0).vertices().size());
        assertEquals(new WorldRenderBatch.Vertex(0, 0, 0, 0xFF112233),
                batch.lines().get(0).vertices().get(0));
        assertEquals(10, batch.lines().get(1).vertices().size());
    }

    @Test
    void groupsBeamAndPlaneIntoOneQuadBatch() {
        WorldRenderFrame frame = new WorldRenderFrame(new Position3D(0, 0, 0), List.of(
                new WorldRenderCommand.VerticalBeam(new Position3D(1, 2, 3), 8, 0.5, 0xAA010203, true),
                new WorldRenderCommand.HorizontalPlane(new Position3D(1, 2, 3), 2, 0xBB040506, true)));

        WorldRenderBatch batch = WorldRenderBatchCompiler.compile(frame);

        assertEquals(1, batch.quads().size());
        assertEquals(20, batch.quads().get(0).vertices().size());
    }
}
