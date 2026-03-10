package fun.prof_chen.teamviewer.multipleplayeresp.renderbridge.abstraction;

import fun.prof_chen.teamviewer.multipleplayeresp.model.Position3D;
import fun.prof_chen.teamviewer.multipleplayeresp.renderbridge.model.AxisAlignedBox3D;

public interface PlayerEspRenderBridge {
	void drawOutlinedBox(RenderContextHandle context, AxisAlignedBox3D box, int color, boolean depthTest);

	void drawLine(RenderContextHandle context, Position3D start, Position3D end, int color, boolean depthTest);

	default void drawTracerLine(RenderContextHandle context, Position3D startPoint, Position3D endPoint, int color, boolean depthTest) {
		drawLine(context, startPoint, endPoint, color, depthTest);
	}

	void drawVerticalBeam(RenderContextHandle context, Position3D baseCenter, double height, double radius, int color, boolean depthTest);

	void drawHorizontalPlane(RenderContextHandle context, Position3D center, double halfSize, int color, boolean depthTest);
}