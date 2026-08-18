package fun.prof_chen.teamviewer.main_code.renderbridge.model;

import java.util.List;

/** Camera-relative position-color vertices grouped for a minimal number of native draw calls. */
public record WorldRenderBatch(List<LineBatch> lines, List<QuadBatch> quads) {
    public WorldRenderBatch {
        lines = lines == null ? List.of() : List.copyOf(lines);
        quads = quads == null ? List.of() : List.copyOf(quads);
    }

    public record Vertex(float x, float y, float z, int color) { }

    public record LineBatch(boolean depthTest, float width, List<Vertex> vertices) {
        public LineBatch {
            vertices = List.copyOf(vertices);
        }
    }

    public record QuadBatch(boolean depthTest, List<Vertex> vertices) {
        public QuadBatch {
            vertices = List.copyOf(vertices);
        }
    }
}
