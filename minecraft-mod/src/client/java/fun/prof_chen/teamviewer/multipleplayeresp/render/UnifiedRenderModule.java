package fun.prof_chen.teamviewer.multipleplayeresp.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.*;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一的3D渲染模块 - 独立的ESP和追踪渲染工具
 * 
 * 特性：
 * - 绘制3D碰撞盒轮廓（Outlined Boxes）
 * - 绘制直线（Lines）
 * - 绘制追踪线条（Tracers）
 * - 支持颜色和深度测试
 * - Minecraft 1.21.8 Fabric API 兼容
 */
public class UnifiedRenderModule {
	
	private static final Logger LOGGER = LoggerFactory.getLogger("UnifiedRenderModule");
	
	// 线条宽度常量
	private static final float DEFAULT_LINE_WIDTH = 2.5F;
	private static final float TRACER_LINE_WIDTH = 1.0F;
	private static final Map<Double, RenderLayer> NO_DEPTH_DEBUG_LINE_LAYER_CACHE = new ConcurrentHashMap<>();

	private static final Method RENDER_LAYER_FACTORY_METHOD;
	private static final Field MULTI_PHASE_PIPELINE_FIELD;
	private static final Field MULTI_PHASE_PHASES_FIELD;

	static {
		Method layerFactory = null;
		Field pipelineField = null;
		Field phasesField = null;
		try {
			Class<?> multiPhaseClass = Class.forName("net.minecraft.client.render.RenderLayer$MultiPhase");
			Class<?> multiPhaseParametersClass = Class.forName("net.minecraft.client.render.RenderLayer$MultiPhaseParameters");
			layerFactory = RenderLayer.class.getDeclaredMethod("of", String.class, int.class, RenderPipeline.class, multiPhaseParametersClass);
			layerFactory.setAccessible(true);
			pipelineField = multiPhaseClass.getDeclaredField("pipeline");
			pipelineField.setAccessible(true);
			phasesField = multiPhaseClass.getDeclaredField("phases");
			phasesField.setAccessible(true);
		} catch (Exception exception) {
			LOGGER.warn("Failed to initialize no-depth RenderLayer reflection hooks: {}", exception.getMessage());
		}
		RENDER_LAYER_FACTORY_METHOD = layerFactory;
		MULTI_PHASE_PIPELINE_FIELD = pipelineField;
		MULTI_PHASE_PHASES_FIELD = phasesField;
	}
	
	/**
	 * 绘制方框轮廓
	 * 表现为一个立方体的12条边
	 * 
	 * @param matrices MatrixStack用于变换矩阵操作
	 * @param box      碰撞盒，包含min和max坐标
	 * @param color    颜色值（ARGB格式，例如：0xFF0000FF代表不透明红色）
	 * @param depthTest 是否启用深度测试
	 */
	public static void drawOutlinedBox(MatrixStack matrices, Box box, int color, boolean depthTest) {
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
		
		drawOutlinedBox(matrices, buffer, box, color);
		
		// 使用 RenderLayer 绘制缓冲区
		getDebugLineStripLayer(DEFAULT_LINE_WIDTH, depthTest).draw(buffer.end());
	}
	
	/**
	 * 绘制方框轮廓（使用现有的BufferBuilder）
	 * 
	 * @param matrices MatrixStack用于变换矩阵操作
	 * @param buffer   BufferBuilder顶点缓冲区
	 * @param box      碰撞盒
	 * @param color    颜色值（ARGB格式）
	 */
	public static void drawOutlinedBox(MatrixStack matrices, BufferBuilder buffer, Box box, int color) {
		float x1 = (float) box.minX;
		float y1 = (float) box.minY;
		float z1 = (float) box.minZ;
		float x2 = (float) box.maxX;
		float y2 = (float) box.maxY;
		float z2 = (float) box.maxZ;
		
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();
		
		// 提取ARGB颜色分量
		float a = ((color >> 24) & 0xFF) / 255.0f;
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		
		// 如果alpha为0，设置默认值（防止完全透明）
		if (a == 0.0f && (color >> 24) == 0) {
			a = 1.0f;
		}
		
		RenderSystem.lineWidth(DEFAULT_LINE_WIDTH);
		
		// 绘制立方体的12条边
		// ===== 底面 =====
		buffer.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y1, z1).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x2, y1, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y1, z2).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x2, y1, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y1, z2).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x1, y1, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
		
		// ===== 顶面 =====
		buffer.vertex(matrix4f, x1, y2, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y2, z1).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x2, y2, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y2, z2).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x1, y2, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y2, z1).color(r, g, b, a);
		
		// ===== 垂直边 =====
		buffer.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y2, z1).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x2, y1, z1).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y2, z1).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x2, y1, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
		
		buffer.vertex(matrix4f, x1, y1, z2).color(r, g, b, a);
		buffer.vertex(matrix4f, x1, y2, z2).color(r, g, b, a);
	}
	
	/**
	 * 绘制单条直线
	 * 
	 * @param matrices MatrixStack用于变换矩阵操作
	 * @param start    起点
	 * @param end      终点
	 * @param color    颜色值（ARGB格式）
	 */
	public static void drawLine(MatrixStack matrices, Vec3d start, Vec3d end, int color) {
		drawLine(matrices, start, end, color, true);
	}

	public static void drawLine(MatrixStack matrices, Vec3d start, Vec3d end, int color, boolean depthTest) {
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
		
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();
		
		// 提取ARGB颜色分量
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
		
		// 绘制缓冲区
		getDebugLineStripLayer(TRACER_LINE_WIDTH, depthTest).draw(buffer.end());
	}
	
	/**
	 * 绘制从起点到终点的追踪线条
	 * 常用于ESP功能中标记敌人或目标
	 * 
	 * @param matrices  MatrixStack用于变换矩阵操作
	 * @param startPoint 起点（通常是摄像机位置向前偏移）
	 * @param endPoint   终点（目标实体的位置）
	 * @param color      颜色值（ARGB格式）
	 */
	public static void drawTracerLine(MatrixStack matrices, Vec3d startPoint, Vec3d endPoint, int color) {
		drawTracerLine(matrices, startPoint, endPoint, color, true);
	}

	public static void drawTracerLine(MatrixStack matrices, Vec3d startPoint, Vec3d endPoint, int color, boolean depthTest) {
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
		
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();
		
		// 提取ARGB颜色分量
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		float a = ((color >> 24) & 0xFF) / 255.0f;
		
		// 如果alpha为0，设置默认值（防止完全透明）
		if (a == 0.0f && (color >> 24) == 0) {
			a = 1.0f;
		}
		
		buffer.vertex(matrix4f, (float) startPoint.x, (float) startPoint.y, (float) startPoint.z).color(r, g, b, a);
		buffer.vertex(matrix4f, (float) endPoint.x, (float) endPoint.y, (float) endPoint.z).color(r, g, b, a);
		
		// 绘制缓冲区
		getDebugLineStripLayer(TRACER_LINE_WIDTH, depthTest).draw(buffer.end());
	}

	private static RenderLayer getDebugLineStripLayer(double lineWidth, boolean depthTest) {
		if (depthTest) {
			return RenderLayer.getDebugLineStrip(lineWidth);
		}
		return NO_DEPTH_DEBUG_LINE_LAYER_CACHE.computeIfAbsent(lineWidth, width -> {
			RenderLayer baseLayer = RenderLayer.getDebugLineStrip(width);
			return createNoDepthLayer(baseLayer, "teamviewer_no_depth_debug_line_strip_" + sanitizeLineWidth(width));
		});
	}

	private static RenderLayer createNoDepthLayer(RenderLayer baseLayer, String newLayerName) {
		if (RENDER_LAYER_FACTORY_METHOD == null || MULTI_PHASE_PIPELINE_FIELD == null || MULTI_PHASE_PHASES_FIELD == null) {
			return baseLayer;
		}
		try {
			RenderPipeline basePipeline = (RenderPipeline) MULTI_PHASE_PIPELINE_FIELD.get(baseLayer);
			Object basePhases = MULTI_PHASE_PHASES_FIELD.get(baseLayer);
			RenderPipeline noDepthPipeline = clonePipelineWithNoDepth(basePipeline, newLayerName);
			return (RenderLayer) RENDER_LAYER_FACTORY_METHOD.invoke(
				null,
				newLayerName,
				baseLayer.getExpectedBufferSize(),
				noDepthPipeline,
				basePhases
			);
		} catch (Exception exception) {
			LOGGER.warn("Failed to create no-depth RenderLayer '{}': {}", newLayerName, exception.getMessage());
			return baseLayer;
		}
	}

	private static RenderPipeline clonePipelineWithNoDepth(RenderPipeline basePipeline, String newPipelineName) {
		RenderPipeline.Builder builder = RenderPipeline.builder();
		builder.withLocation(newPipelineName);
		builder.withVertexShader(basePipeline.getVertexShader());
		builder.withFragmentShader(basePipeline.getFragmentShader());

		Defines defines = basePipeline.getShaderDefines();
		if (defines != null && !defines.isEmpty()) {
			for (String flag : defines.flags()) {
				builder.withShaderDefine(flag);
			}
			for (Map.Entry<String, String> defineEntry : defines.values().entrySet()) {
				String key = defineEntry.getKey();
				String value = defineEntry.getValue();
				if (key == null || key.isBlank() || value == null || value.isBlank()) {
					continue;
				}
				try {
					builder.withShaderDefine(key, Integer.parseInt(value));
					continue;
				} catch (NumberFormatException ignored) {
				}
				try {
					builder.withShaderDefine(key, Float.parseFloat(value));
				} catch (NumberFormatException ignored) {
					LOGGER.debug("Skip non-numeric shader define {}={} for no-depth pipeline clone", key, value);
				}
			}
		}

		for (String sampler : basePipeline.getSamplers()) {
			builder.withSampler(sampler);
		}
		for (RenderPipeline.UniformDescription uniform : basePipeline.getUniforms()) {
			if (uniform.textureFormat() != null && uniform.type() != null) {
				builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
			} else if (uniform.type() != null) {
				builder.withUniform(uniform.name(), uniform.type());
			}
		}

		builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
		builder.withPolygonMode(basePipeline.getPolygonMode());
		builder.withCull(basePipeline.isCull());
		Optional<BlendFunction> blendFunction = basePipeline.getBlendFunction();
		if (blendFunction.isPresent()) {
			builder.withBlend(blendFunction.get());
		} else {
			builder.withoutBlend();
		}
		builder.withColorWrite(basePipeline.isWriteColor(), basePipeline.isWriteAlpha());
		builder.withDepthWrite(basePipeline.isWriteDepth());
		builder.withColorLogic(basePipeline.getColorLogic());
		builder.withVertexFormat(basePipeline.getVertexFormat(), basePipeline.getVertexFormatMode());
		builder.withDepthBias(basePipeline.getDepthBiasScaleFactor(), basePipeline.getDepthBiasConstant());
		return builder.build();
	}

	private static String sanitizeLineWidth(double lineWidth) {
		return String.valueOf(lineWidth).replace('.', '_').replace('-', '_');
	}
	
	/**
	 * 将RGB浮点值转换为ARGB整数颜色
	 * 
	 * @param red   红色分量（0.0-1.0）
	 * @param green 绿色分量（0.0-1.0）
	 * @param blue  蓝色分量（0.0-1.0）
	 * @param alpha 透明度（0.0-1.0）
	 * @return 整数ARGB颜色值
	 */
	public static int colorToARGB(float red, float green, float blue, float alpha) {
		return ((int)(alpha * 255) << 24) | ((int)(red * 255) << 16) | 
		       ((int)(green * 255) << 8) | ((int)(blue * 255));
	}
	
	/**
	 * 根据距离计算渐变颜色
	 * 距离越近越橙红色，距离越远越绿色
	 * 
	 * @param distance 距离值
	 * @return ARGB颜色值
	 */
	public static int getDistanceColor(float distance) {
		// 归一化距离（以20为参考单位）
		float normalizedDist = distance / 20.0F;
		
		// 红色：从1.0衰减到0.0
		float red = Math.max(0, 2.0F - normalizedDist);
		red = Math.min(1, red);
		
		// 绿色：从0.0增长到1.0
		float green = Math.min(1, normalizedDist);
		
		// 蓝色：始终为0
		float blue = 0;
		
		// 返回RGB颜色，带50%透明度
		return colorToARGB(red, green, blue, 0.5F);
	}
	
	/**
	 * 计算敌人颜色（红色）
	 * 
	 * @return ARGB颜色值（红色 0x80FF0000）
	 */
	public static int getEnemyColor() {
		return 0x80FF0000;
	}
	
	/**
	 * 计算朋友颜色（蓝紫色）
	 * 
	 * @return ARGB颜色值（深蓝 0x800000FF）
	 */
	public static int getFriendColor() {
		return 0x800000FF;
	}
	
	/**
	 * 计算中立颜色（黄色）
	 * 
	 * @return ARGB颜色值（黄色 0x80FFFF00）
	 */
	public static int getNeutralColor() {
		return 0x80FFFF00;
	}
	
	/**
	 * 计算自己颜色（绿色）
	 * 
	 * @return ARGB颜色值（绿色 0x8000FF00）
	 */
	public static int getSelfColor() {
		return 0x8000FF00;
	}
}
