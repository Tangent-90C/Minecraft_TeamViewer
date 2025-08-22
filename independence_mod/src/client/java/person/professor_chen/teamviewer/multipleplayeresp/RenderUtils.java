package person.professor_chen.teamviewer.multipleplayeresp;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;

public class RenderUtils {

	public static void drawOutlinedBox(MatrixStack matrices, Box box, int color, boolean depthTest) {
		int depthFunc = depthTest ? 515 : 519; // GL_LEQUAL : GL_ALWAYS
		RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(depthFunc);

		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

		drawOutlinedBox(matrices, buffer, box, color);

		BufferRenderer.drawWithGlobalProgram(buffer.end());
	}

	public static void drawOutlinedBox(MatrixStack matrices, BufferBuilder buffer, Box box, int color) {
        float x1 = (float)box.minX;
		float y1 = (float)box.minY;
		float z1 = (float)box.minZ;
		float x2 = (float)box.maxX;
		float y2 = (float)box.maxY;
		float z2 = (float)box.maxZ;

		Matrix4f matrix4f = matrices.peek().getPositionMatrix();

		// 正确提取ARGB颜色分量
		float a = ((color >> 24) & 0xFF) / 255.0f;
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;

		// 如果alpha为0，设置默认值（防止完全透明）
		if (a == 0.0f && (color >> 24) == 0) {
			a = 1.0f;
		}

        RenderSystem.lineWidth(2.5F);

		// 绘制立方体的12条边
		// 底面
		buffer.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y1, z1).color(r, g, b, a);

		buffer.vertex(matrix4f, x2, y1, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y1, z2).color(r, g, b, a);

		buffer.vertex(matrix4f, x2, y1, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y1, z2).color(r, g, b, a);

		buffer.vertex(matrix4f, x1, y1, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);

		// 顶面
		buffer.vertex(matrix4f, x1, y2, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y2, z1).color(r, g, b, a);

		buffer.vertex(matrix4f, x2, y2, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);

		buffer.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y2, z2).color(r, g, b, a);

		buffer.vertex(matrix4f, x1, y2, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y2, z1).color(r, g, b, a);

		// 垂直边
		buffer.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y2, z1).color(r, g, b, a);

		buffer.vertex(matrix4f, x2, y1, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y2, z1).color(r, g, b, a);

		buffer.vertex(matrix4f, x2, y1, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);

		buffer.vertex(matrix4f, x1, y1, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y2, z2).color(r, g, b, a);

	}

	public static void drawLine(MatrixStack matrices, Vec3d start, Vec3d end, int color) {
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);

		Matrix4f matrix4f = matrices.peek().getPositionMatrix();

		// 正确提取ARGB颜色分量
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		float a = ((color >> 24) & 0xFF) / 255.0f;

		// 如果alpha为0，设置默认值（防止完全透明）
		if (a == 0.0f && (color >> 24) == 0) {
			a = 1.0f;
		}

		buffer.vertex(matrix4f, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, a);
		buffer.vertex(matrix4f, (float) end.x, (float) end.y, (float) end.z).color(r, g, b, a);

		BufferRenderer.drawWithGlobalProgram(buffer.end());
	}
}