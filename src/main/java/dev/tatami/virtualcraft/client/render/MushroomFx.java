package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tatami.virtualcraft.VirtualCraft;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Textured, per-frame VFX batch for the Mushroom King: soft additive glows, flares,
 * shockwave and sigil decals, trails and textured telegraph fills.
 *
 * <p>Callers queue quads in world space (the pose already carries the camera offset)
 * and call {@link #flush} once. Alpha-blended quads draw first so the additive light
 * sits on top of the darker readable shapes.
 */
final class MushroomFx {
	enum Tex {
		GLOW("glow"), FLARE("flare"), SHOCKWAVE("shockwave"), SIGIL("sigil"),
		FILL("fill"), TRAIL("trail"), SPORE_ORB("spore_orb");

		final ResourceLocation location;

		Tex(String name) {
			location = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,
					"textures/effect/mushroom_king/" + name + ".png");
		}
	}

	/** Bioluminescent signature colours shared by telegraphs, releases and particles. */
	static final int MYCELIUM = 0x5CFFD2;
	static final int MYCELIUM_DEEP = 0x138C7A;
	static final int SPORE = 0xFF5FB0;
	static final int SPORE_PALE = 0xFFC6E4;
	static final int AMBER = 0xFFB23D;
	static final int AMBER_PALE = 0xFFE7B0;
	static final int SKY = 0x5BC8FF;
	static final int SKY_PALE = 0xCDEFFF;
	static final int BONE = 0xFFF0D8;

	private final Map<Tex, List<float[]>> additive = new EnumMap<>(Tex.class);
	private final Map<Tex, List<float[]>> blended = new EnumMap<>(Tex.class);

	void clear() {
		additive.clear();
		blended.clear();
	}

	boolean isEmpty() {
		return additive.isEmpty() && blended.isEmpty();
	}

	/** Camera-facing sprite. */
	void billboard(Tex tex, Vec3 center, Vec3 camera, double size, double roll,
			int rgb, float alpha) {
		// Light accents must not turn every joint into the same translucent plastic bead.
		if (tex == Tex.GLOW) { size *= 0.5; alpha *= 0.28F; }
		if (tex == Tex.SPORE_ORB) { size *= 0.65; alpha *= 0.4F; }
		if (alpha <= 0.004F || size <= 0) return;
		Vec3 view = camera.subtract(center);
		if (view.lengthSqr() < 1.0E-6) return;
		view = view.normalize();
		Vec3 right = view.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
		right = right.normalize();
		Vec3 up = right.cross(view).normalize();
		double cos = Math.cos(roll) * size, sin = Math.sin(roll) * size;
		Vec3 r = right.scale(cos).add(up.scale(sin));
		Vec3 u = up.scale(cos).subtract(right.scale(sin));
		quad(tex, true, center.subtract(r).subtract(u), center.add(r).subtract(u),
				center.add(r).add(u), center.subtract(r).add(u), 0, 1, 1, 0, rgb, alpha, alpha);
	}

	/** Horizontal decal centred on {@code center}, rotated around Y. */
	void ground(Tex tex, Vec3 center, double halfSize, double rotation, int rgb, float alpha,
			boolean add) {
		if (tex == Tex.GLOW) alpha *= 0.22F;
		if (alpha <= 0.004F || halfSize <= 0) return;
		double cos = Math.cos(rotation) * halfSize, sin = Math.sin(rotation) * halfSize;
		Vec3 a = new Vec3(cos, 0, sin), b = new Vec3(-sin, 0, cos);
		quad(tex, add, center.subtract(a).subtract(b), center.add(a).subtract(b),
				center.add(a).add(b), center.subtract(a).add(b), 0, 0, 1, 1, rgb, alpha, alpha);
	}

	/** Soft glowing line lying on the ground; uses the glow sheet's radial profile across. */
	void groundGlowLine(Vec3 from, Vec3 to, double width, int rgb, float alpha) {
		Vec3 axis = to.subtract(from);
		if (axis.lengthSqr() < 1.0E-7 || alpha <= 0.004F) return;
		Vec3 side = new Vec3(-axis.z, 0, axis.x).normalize().scale(width);
		quad(Tex.GLOW, true, from.add(side), to.add(side), to.subtract(side), from.subtract(side),
				.5F, 0, .5F, 1, rgb, alpha, alpha);
	}

	/** Camera-facing streak with the bright head at {@code to}. */
	void trail(Vec3 from, Vec3 to, Vec3 camera, double width, int rgb, float alpha) {
		Vec3 axis = to.subtract(from);
		if (axis.lengthSqr() < 1.0E-7 || alpha <= 0.004F) return;
		Vec3 side = axis.cross(camera.subtract(from.add(to).scale(.5)));
		if (side.lengthSqr() < 1.0E-7) return;
		side = side.normalize().scale(width);
		quad(Tex.TRAIL, true, from.subtract(side), to.subtract(side), to.add(side), from.add(side),
				0, 1, 1, 0, rgb, alpha, alpha);
	}

	/**
	 * Filled ground sector. The mycelial net is tiled in world space so it never stretches;
	 * {@code fill} (0..1) brightens the part of the sector that the charge has reached.
	 */
	void groundSector(Vec3 center, double innerRadius, double radius, double heading,
			double halfAngle, float fill, int rgb, float alpha) {
		if (alpha <= 0.004F || radius <= innerRadius) return;
		int segments = Mth.clamp((int)Math.ceil(halfAngle * 2 * radius / .6), 6, 64);
		double reached = innerRadius + (radius - innerRadius) * Mth.clamp(fill, 0, 1);
		for (int i = 0; i < segments; i++) {
			double a0 = heading - halfAngle + halfAngle * 2 * i / segments;
			double a1 = heading - halfAngle + halfAngle * 2 * (i + 1) / segments;
			sectorBand(center, innerRadius, reached, a0, a1, rgb, alpha * .55F, alpha * .9F);
			sectorBand(center, reached, radius, a0, a1, rgb, alpha * .22F, alpha * .28F);
		}
	}

	/** Filled ground rectangle from {@code origin} along {@code forward}, charge-filled lengthwise. */
	void groundLane(Vec3 origin, Vec3 forward, double length, double halfWidth, float fill,
			int rgb, float alpha) {
		if (alpha <= 0.004F || length <= 0) return;
		Vec3 side = new Vec3(-forward.z, 0, forward.x).scale(halfWidth);
		double reached = length * Mth.clamp(fill, 0, 1);
		int steps = Mth.clamp((int)Math.ceil(length / .8), 1, 32);
		for (int i = 0; i < steps; i++) {
			double s0 = length * i / steps, s1 = length * (i + 1) / steps;
			boolean charged = s1 <= reached;
			float near = charged ? alpha * .9F : alpha * .26F;
			Vec3 a = origin.add(forward.scale(s0)), b = origin.add(forward.scale(s1));
			worldQuad(a.add(side), b.add(side), b.subtract(side), a.subtract(side), rgb, near, near);
		}
	}

	private void sectorBand(Vec3 center, double r0, double r1, double a0, double a1,
			int rgb, float innerAlpha, float outerAlpha) {
		if (r1 - r0 < 1.0E-4) return;
		Vec3 i0 = center.add(Math.cos(a0) * r0, 0, Math.sin(a0) * r0);
		Vec3 i1 = center.add(Math.cos(a1) * r0, 0, Math.sin(a1) * r0);
		Vec3 o1 = center.add(Math.cos(a1) * r1, 0, Math.sin(a1) * r1);
		Vec3 o0 = center.add(Math.cos(a0) * r1, 0, Math.sin(a0) * r1);
		worldQuad(i0, i1, o1, o0, rgb, innerAlpha, outerAlpha);
	}

	/** Quad using world-space UVs on the fill net: one repeat every two blocks. */
	private void worldQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float nearAlpha, float farAlpha) {
		List<float[]> out = blended.computeIfAbsent(Tex.FILL, key -> new ArrayList<>());
		float[] alpha = {nearAlpha, nearAlpha, farAlpha, farAlpha};
		Vec3[] points = {a, b, c, d};
		float[] quad = new float[4 * 7];
		for (int i = 0; i < 4; i++) {
			put(quad, i, points[i], (float)(points[i].x * .5), (float)(points[i].z * .5), rgb, alpha[i]);
		}
		out.add(quad);
	}

	void quad(Tex tex, boolean add, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
			float u0, float v0, float u1, float v1, int rgb, float alphaAB, float alphaCD) {
		if (Math.max(alphaAB, alphaCD) <= 0.004F) return;
		List<float[]> out = (add ? additive : blended).computeIfAbsent(tex, key -> new ArrayList<>());
		float[] quad = new float[4 * 7];
		put(quad, 0, a, u0, v0, rgb, alphaAB);
		put(quad, 1, b, u1, v0, rgb, alphaAB);
		put(quad, 2, c, u1, v1, rgb, alphaCD);
		put(quad, 3, d, u0, v1, rgb, alphaCD);
		out.add(quad);
	}

	private static void put(float[] quad, int index, Vec3 p, float u, float v, int rgb, float alpha) {
		int o = index * 7;
		quad[o] = (float)p.x;
		quad[o + 1] = (float)p.y;
		quad[o + 2] = (float)p.z;
		quad[o + 3] = u;
		quad[o + 4] = v;
		quad[o + 5] = Float.intBitsToFloat(rgb & 0xFFFFFF);
		quad[o + 6] = Mth.clamp(alpha, 0, 1);
	}

	/** Draws and clears everything queued. Leaves blend enabled with the default function. */
	void flush(Matrix4f pose) {
		if (isEmpty()) return;
		RenderSystem.enableBlend();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		try {
			RenderSystem.defaultBlendFunc();
			draw(pose, blended);
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			draw(pose, additive);
		} finally {
			RenderSystem.defaultBlendFunc();
			clear();
		}
	}

	private static void draw(Matrix4f pose, Map<Tex, List<float[]>> batches) {
		for (Map.Entry<Tex, List<float[]>> entry : batches.entrySet()) {
			List<float[]> quads = entry.getValue();
			if (quads.isEmpty()) continue;
			try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads.size() * 6 * 24)) {
				BufferBuilder out = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES,
						DefaultVertexFormat.POSITION_TEX_COLOR);
				for (float[] q : quads) {
					emit(out, pose, q, 0);
					emit(out, pose, q, 1);
					emit(out, pose, q, 2);
					emit(out, pose, q, 0);
					emit(out, pose, q, 2);
					emit(out, pose, q, 3);
				}
				MeshData mesh = out.build();
				if (mesh != null) {
					RenderSystem.setShaderTexture(0, entry.getKey().location);
					BufferUploader.drawWithShader(mesh);
				}
			}
		}
	}

	private static void emit(BufferBuilder out, Matrix4f pose, float[] q, int index) {
		int o = index * 7;
		int rgb = Float.floatToRawIntBits(q[o + 5]);
		out.addVertex(pose, q[o], q[o + 1], q[o + 2]).setUv(q[o + 3], q[o + 4])
				.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.round(q[o + 6] * 255));
	}

	static int lerpColor(int from, int to, float t) {
		t = Mth.clamp(t, 0, 1);
		int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
