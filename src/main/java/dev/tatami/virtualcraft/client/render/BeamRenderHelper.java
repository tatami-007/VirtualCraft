package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 光束 prism 渲染辅助。
 *
 * <p>1:1 沿用 ME-Beam-Former 1.21.1 (LGPL-3.0) 的 {@code BeamRenderHelper}，
 * 但去掉了 AE2 颜色解析方法（{@code unpackColor / getColorableBlockEntityColor /
 * getPartHostColor / resolveBlockEndpointColor / blendEndpointColors / getEndpointBackDirection}）——
 * VirtualCraft 不依赖 AE2，无线塔的光束颜色由调用方直接提供 RGB。</p>
 *
 * <p>核心方法 {@link #renderColoredBeamVector} 在塔和目标之间绘制一个四面棱柱光束，
 * 用 minecraft 信标光束纹理（{@code minecraft:textures/entity/beacon_beam.png}）+
 * {@link RenderType#beaconBeam} 渲染管线，所以光束自带"流光"动画（V 坐标随时间滚动）+ emissive 高亮。</p>
 *
 * <h3>视觉参数</h3>
 * <ul>
 *   <li>{@link #OUTER_SCALE} = 1.18：外壳光束半径放大系数（柔和的辉光）</li>
 *   <li>{@link #CORE_SCALE} = 0.62：内核光束半径缩小系数（明亮的中心）</li>
 *   <li>{@link #OUTER_ALPHA} = 0.22：外壳半透明度</li>
 *   <li>{@link #CORE_ALPHA} = 0.90：内核近不透明</li>
 *   <li>{@link #CORE_WHITE_MIX} = 0.16：内核与白色混合比例（让中心更亮更"激光感"）</li>
 * </ul>
 */
public final class BeamRenderHelper {

	public static final ResourceLocation BEAM_TEX =
			ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");

	/**
	 * 与 vanilla {@link RenderType#beaconBeam(ResourceLocation, boolean)} 几乎一致，但禁用了
	 * 深度测试 ({@link RenderStateShard#NO_DEPTH_TEST})——修复"塔→机器之间夹着另一台机器时，光束被中间机器遮挡，
	 * 玩家看不到光束打在目标的高亮立方体上"的问题。
	 *
	 * <p>需要外部 ({@code BoundMachineHighlighter}) 在 {@code endBatch(BEAM_NO_DEPTH)} 时拿到这个
	 * RenderType 实例做精准 flush，所以暴露为 {@code public}。</p>
	 *
	 * <h3>与 beaconBeam 的差异</h3>
	 * <ul>
	 *   <li>{@link RenderStateShard#LEQUAL_DEPTH_TEST} → {@link RenderStateShard#NO_DEPTH_TEST}：穿透机器渲染</li>
	 *   <li>新增 {@link RenderStateShard#NO_CULL}：从光束 prism 内部反向看时也保留可见性</li>
	 *   <li>{@link RenderStateShard#COLOR_WRITE}：和 beaconBeam (translucent 分支) 一致，光束不写深度，避免遮住后面的高亮立方体</li>
	 * </ul>
	 */
	public static final RenderType BEAM_NO_DEPTH = RenderType.create(
			"virtual_craft:beam_no_depth",
			DefaultVertexFormat.BLOCK,
			VertexFormat.Mode.QUADS,
			1536,
			false,
			// sortOnUpload=false：vanilla beaconBeam 用 true 是因为它在 LEQUAL 深度测试下半透明面的
			// 排序影响混合结果。我们禁用了深度测试，深度排序无意义，且 sortQuads 会回写 sharedBuffer，
			// 在和后续 HIGHLIGHT_CUBE 共用 sharedBuffer 的场景下观察到会让 cube 渲染消失（见 BoundMachineHighlighter 注释）。
			false,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.RENDERTYPE_BEACON_BEAM_SHADER)
					.setTextureState(new RenderStateShard.TextureStateShard(BEAM_TEX, false, false))
					.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
					.setWriteMaskState(RenderStateShard.COLOR_WRITE)
					.setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
					.setCullState(RenderStateShard.NO_CULL)
					.createCompositeState(false));

	private static final int FULL_BRIGHT = 0x00F000F0;
	private static final float OUTER_SCALE = 1.18f;
	private static final float CORE_SCALE = 0.62f;
	private static final float OUTER_ALPHA = 0.22f;
	private static final float CORE_ALPHA = 0.90f;
	private static final float CORE_WHITE_MIX = 0.16f;
	private static final double BLOCK_BEAM_SHIFT = -0.25d;

	private BeamRenderHelper() {
	}

	/**
	 * 渲染一束 RGB 染色的光束 prism，从当前 PoseStack 原点出发沿 (vx, vy, vz) 向量。
	 *
	 * <p>调用方需要在 push pose 之后 translate 到光束起点，然后传入相对方向向量。
	 * {@code thickness} 是棱柱直径（方块单位）。</p>
	 */
	public static void renderColoredBeamVector(
			PoseStack poseStack, MultiBufferSource buffers,
			float vx, float vy, float vz,
			float r, float g, float b,
			int light, int overlay, float thickness) {
		final double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (length <= 1.0E-6d) {
			return;
		}
		final double invLength = 1.0d / length;

		poseStack.pushPose();
		poseStack.translate(0.5d, 0.5d, 0.5d);
		poseStack.translate(
				vx * invLength * BLOCK_BEAM_SHIFT,
				vy * invLength * BLOCK_BEAM_SHIFT,
				vz * invLength * BLOCK_BEAM_SHIFT);
		renderBeamPrism(poseStack, buffers, vx, vy, vz, r, g, b, light, overlay, thickness);
		poseStack.popPose();
	}

	private static void renderBeamPrism(
			PoseStack poseStack, MultiBufferSource buffers,
			double vx, double vy, double vz,
			float r, float g, float b,
			int light, int overlay, float thickness) {
		final double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (length <= 1.0E-6d) {
			return;
		}

		final float[] displayColor = normalizeDisplayColor(r, g, b);
		final float[] coreColor = mixWithWhite(displayColor, CORE_WHITE_MIX);
		final float radius = Math.max(0.01f, thickness) * 0.5f;

		PoseStack.Pose last = poseStack.last();
		Matrix4f pose = last.pose();
		// 改用 BEAM_NO_DEPTH：原 vanilla beaconBeam 有 LEQUAL 深度测试，被中间机器挡住光束就被剪断，
		// 玩家看不到光束"打到"目标高亮立方体上。无深度测试版本让光束始终绘制在场景之上。
		VertexConsumer consumer = buffers.getBuffer(BEAM_NO_DEPTH);
		float v0 = getTextureScroll();
		float v1 = v0 + (float) length * 1.6f;

		// 外壳（柔光）
		emitBeamShell(pose, last, consumer, vx, vy, vz, radius * OUTER_SCALE,
				displayColor, OUTER_ALPHA, overlay, v0, v1);
		// 内核（高亮）
		emitBeamShell(pose, last, consumer, vx, vy, vz, radius * CORE_SCALE,
				coreColor, CORE_ALPHA, overlay, v0, v1);
	}

	private static void emitBeamShell(
			Matrix4f pose, PoseStack.Pose poseInfo, VertexConsumer consumer,
			double vx, double vy, double vz, float radius,
			float[] color, float alpha, int overlay, float v0, float v1) {
		final double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
		final double nx = vx / length;
		final double ny = vy / length;
		final double nz = vz / length;

		// 选一个不平行于光束方向的参考向量来构造正交基
		final double refX, refY, refZ;
		if (Math.abs(ny) < 0.92d) {
			refX = 0.0d;
			refY = 1.0d;
			refZ = 0.0d;
		} else {
			refX = 1.0d;
			refY = 0.0d;
			refZ = 0.0d;
		}

		double sideX = refY * nz - refZ * ny;
		double sideY = refZ * nx - refX * nz;
		double sideZ = refX * ny - refY * nx;
		double sideLength = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
		if (sideLength <= 1.0E-6d) {
			return;
		}
		sideX = sideX / sideLength * radius;
		sideY = sideY / sideLength * radius;
		sideZ = sideZ / sideLength * radius;

		double upX = ny * sideZ - nz * sideY;
		double upY = nz * sideX - nx * sideZ;
		double upZ = nx * sideY - ny * sideX;
		double upLength = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
		if (upLength <= 1.0E-6d) {
			return;
		}
		upX = upX / upLength * radius;
		upY = upY / upLength * radius;
		upZ = upZ / upLength * radius;

		// 起点四角
		float sx0 = (float) (-sideX - upX), sy0 = (float) (-sideY - upY), sz0 = (float) (-sideZ - upZ);
		float sx1 = (float) (sideX - upX), sy1 = (float) (sideY - upY), sz1 = (float) (sideZ - upZ);
		float sx2 = (float) (sideX + upX), sy2 = (float) (sideY + upY), sz2 = (float) (sideZ + upZ);
		float sx3 = (float) (-sideX + upX), sy3 = (float) (-sideY + upY), sz3 = (float) (-sideZ + upZ);

		// 终点四角 = 起点四角 + 光束向量
		float ex0 = (float) (vx + sx0), ey0 = (float) (vy + sy0), ez0 = (float) (vz + sz0);
		float ex1 = (float) (vx + sx1), ey1 = (float) (vy + sy1), ez1 = (float) (vz + sz1);
		float ex2 = (float) (vx + sx2), ey2 = (float) (vy + sy2), ez2 = (float) (vz + sz2);
		float ex3 = (float) (vx + sx3), ey3 = (float) (vy + sy3), ez3 = (float) (vz + sz3);

		// 4 个侧面（双面渲染避免被剔除）
		emitFace(pose, poseInfo, consumer, sx0, sy0, sz0, sx1, sy1, sz1, ex1, ey1, ez1, ex0, ey0, ez0, color, alpha, overlay, v0, v1);
		emitFace(pose, poseInfo, consumer, sx1, sy1, sz1, sx2, sy2, sz2, ex2, ey2, ez2, ex1, ey1, ez1, color, alpha, overlay, v0, v1);
		emitFace(pose, poseInfo, consumer, sx2, sy2, sz2, sx3, sy3, sz3, ex3, ey3, ez3, ex2, ey2, ez2, color, alpha, overlay, v0, v1);
		emitFace(pose, poseInfo, consumer, sx3, sy3, sz3, sx0, sy0, sz0, ex0, ey0, ez0, ex3, ey3, ez3, color, alpha, overlay, v0, v1);
	}

	private static void emitFace(
			Matrix4f pose, PoseStack.Pose poseInfo, VertexConsumer consumer,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4,
			float[] color, float alpha, int overlay, float v0, float v1) {
		float[] faceNormal = computeNormal(x1, y1, z1, x2, y2, z2, x4, y4, z4);
		quadBothSides(pose, poseInfo, consumer,
				x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4,
				color[0], color[1], color[2], alpha,
				0.0f, v0, 1.0f, v1, overlay,
				faceNormal[0], faceNormal[1], faceNormal[2]);
	}

	/**
	 * 把任意输入颜色拉到"显著饱和"区间。原理：
	 * (1) 若颜色接近灰色（饱和度 &lt; 0.08）或接近黑色，回退到纯白；
	 * (2) 否则按 max 通道归一化到 0.92 然后做 {@code 1.28x} 饱和度增强。
	 *
	 * <p>这样保证即使调用方传 (1, 0, 0) 这种"原始"颜色，渲染出的光束也是高亮、有冲击力的，
	 * 而不是被 emissive 渲染管线冲淡变成淡红。</p>
	 */
	private static float[] normalizeDisplayColor(float r, float g, float b) {
		float cr = clamp01(r);
		float cg = clamp01(g);
		float cb = clamp01(b);

		float max = Math.max(cr, Math.max(cg, cb));
		if (max <= 1.0E-4f) {
			return new float[]{1.0f, 1.0f, 1.0f};
		}
		float min = Math.min(cr, Math.min(cg, cb));
		float saturation = (max - min) / max;
		if (saturation < 0.08f) {
			return new float[]{1.0f, 1.0f, 1.0f};
		}

		float gain = 0.92f / max;
		cr = clamp01(cr * gain);
		cg = clamp01(cg * gain);
		cb = clamp01(cb * gain);

		float average = (cr + cg + cb) / 3.0f;
		float saturationBoost = 1.28f;
		cr = clamp01(average + (cr - average) * saturationBoost);
		cg = clamp01(average + (cg - average) * saturationBoost);
		cb = clamp01(average + (cb - average) * saturationBoost);
		return new float[]{cr, cg, cb};
	}

	private static float[] mixWithWhite(float[] color, float amount) {
		float mix = clamp01(amount);
		return new float[]{
				color[0] * (1.0f - mix) + mix,
				color[1] * (1.0f - mix) + mix,
				color[2] * (1.0f - mix) + mix
		};
	}

	private static float[] computeNormal(
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x4, float y4, float z4) {
		float ax = x2 - x1, ay = y2 - y1, az = z2 - z1;
		float bx = x4 - x1, by = y4 - y1, bz = z4 - z1;
		float nx = ay * bz - az * by;
		float ny = az * bx - ax * bz;
		float nz = ax * by - ay * bx;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length <= 1.0E-6f) {
			return new float[]{0.0f, 1.0f, 0.0f};
		}
		return new float[]{nx / length, ny / length, nz / length};
	}

	/** beacon beam 纹理 V 坐标按游戏时间滚动，制造"流光下行"效果。 */
	private static float getTextureScroll() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 0.0f;
		}
		return -(mc.level.getGameTime() % 4000L) * 0.05f;
	}

	private static void quadBothSides(
			Matrix4f pose, PoseStack.Pose poseInfo, VertexConsumer consumer,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4,
			float r, float g, float b, float a,
			float u0, float v0, float u1, float v1,
			int overlay, float nx, float ny, float nz) {
		quad(pose, poseInfo, consumer,
				x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4,
				r, g, b, a, u0, v0, u1, v1, overlay, nx, ny, nz);
		// 反向缠绕 + 反向法线渲染背面（避免视角穿过光束时光束消失）
		quad(pose, poseInfo, consumer,
				x4, y4, z4, x3, y3, z3, x2, y2, z2, x1, y1, z1,
				r, g, b, a, u0, v0, u1, v1, overlay, -nx, -ny, -nz);
	}

	private static void quad(
			Matrix4f pose, PoseStack.Pose poseInfo, VertexConsumer consumer,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4,
			float r, float g, float b, float a,
			float u0, float v0, float u1, float v1,
			int overlay, float nx, float ny, float nz) {
		// NeoForge 1.21.1 的 setNormal 接受 PoseStack.Pose 而不是 Matrix3f
		consumer.addVertex(pose, x1, y1, z1)
				.setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(FULL_BRIGHT).setNormal(poseInfo, nx, ny, nz);
		consumer.addVertex(pose, x2, y2, z2)
				.setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(FULL_BRIGHT).setNormal(poseInfo, nx, ny, nz);
		consumer.addVertex(pose, x3, y3, z3)
				.setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(FULL_BRIGHT).setNormal(poseInfo, nx, ny, nz);
		consumer.addVertex(pose, x4, y4, z4)
				.setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(FULL_BRIGHT).setNormal(poseInfo, nx, ny, nz);
	}

	private static float clamp01(float value) {
		if (value < 0.0f) {
			return 0.0f;
		}
		if (value > 1.0f) {
			return 1.0f;
		}
		return value;
	}
}
