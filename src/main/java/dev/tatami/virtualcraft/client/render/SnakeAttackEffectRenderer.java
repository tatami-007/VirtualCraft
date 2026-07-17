package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tatami.virtualcraft.boss.SnakeHuntPattern;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Additive combat geometry layered over the readable range telegraphs.
 *
 * <p>Endpoints come from the snake's real multipart hitboxes. The arcs keep
 * fixed endpoints and only disturb interior points, matching the actual attack
 * instead of producing free-floating particle noise.
 */
public final class SnakeAttackEffectRenderer {
	private SnakeAttackEffectRenderer() {
	}

	public static void render(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
		float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
				GlStateManager.SourceFactor.SRC_ALPHA,
				GlStateManager.DestFactor.ONE,
				GlStateManager.SourceFactor.ONE,
				GlStateManager.DestFactor.ZERO);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);

		BufferBuilder buffer = Tesselator.getInstance().begin(
				VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = poseStack.last().pose();
		boolean wrote = false;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof VirtualSnakeKing snake) || !snake.isAlive()) {
				continue;
			}
			wrote |= renderSignalArcs(buffer, pose, snake, camera, partialTick);
			wrote |= renderAttackTrails(buffer, pose, snake, camera, partialTick);
		}
		MeshData mesh = buffer.build();
		if (mesh != null) {
			if (wrote) {
				BufferUploader.drawWithShader(mesh);
			} else {
				mesh.close();
			}
		}

		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		poseStack.popPose();
	}

	private static boolean renderSignalArcs(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, float partialTick) {
		float strength = snake.getSignalStrength();
		if (snake.getSignalType() == VirtualSnakeKing.SIGNAL_NONE || strength < 0.12F) {
			return false;
		}
		Palette palette = Palette.WHITE_GOLD;
		int phase = snake.getAttackTick() / 2;
		for (int i = 0; i < 5; i++) {
			Vec3 from = snake.getCombatPartPosition(i, partialTick);
			Vec3 to = snake.getCombatPartPosition(i + 1, partialTick);
			drawArc(buffer, pose, from, to, camera, 4,
					0.035F + strength * 0.07F,
					snake.getId() * 73471L + phase * 97L + i * 31L,
					palette, strength * (0.26F + i * 0.035F));
		}
		Vec3 head = snake.getCombatPartPosition(0, partialTick);
		Vec3 forward = lockedForward(snake);
		Vec3 focus = head.add(forward.scale(1.8 + strength * 0.8)).add(0.0, 0.25, 0.0);
		drawArc(buffer, pose, head.add(0.0, 0.45, 0.0), focus, camera, 7,
				0.16F + strength * 0.2F, snake.getId() * 193L + phase * 43L,
				palette, Math.min(1.0F, strength + 0.15F));
		return true;
	}

	private static boolean renderAttackTrails(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, float partialTick) {
		int attack = snake.getAttackState();
		int tick = snake.getAttackTick();
		if (isBiteFrame(snake, attack, tick)) {
			float local = biteLocalTick(snake, attack, tick);
			float impact = 1.0F - Math.min(1.0F,
					Math.abs(local - SnakeHuntPattern.BITE_HIT_TICK) / 5.0F);
			Vec3 head = snake.getCombatPartPosition(0, partialTick).add(0.0, 0.18, 0.0);
			Vec3 forward = lockedForward(snake);
			Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
			Vec3 focus = snake.position().add(forward.scale(3.35)).add(0.0, 1.15, 0.0);
			Palette bite = Palette.WHITE_GOLD;
			drawArc(buffer, pose, head.add(side.scale(0.52)), focus.add(side.scale(0.18)),
					camera, 6, 0.16F, snake.getId() * 109L + tick * 17L, bite, 0.55F + impact * 0.45F);
			drawArc(buffer, pose, head.add(side.scale(-0.52)), focus.add(side.scale(-0.18)),
					camera, 6, 0.16F, snake.getId() * 149L + tick * 19L, bite, 0.55F + impact * 0.45F);
			return true;
		}
		if (isTailFrame(snake, attack, tick)) {
			float intensity = tailIntensity(snake, attack, tick);
			for (int i = 3; i < 6; i++) {
				Vec3 previous = snake.getPreviousCombatPartPosition(i);
				Vec3 current = snake.getCombatPartPosition(i, partialTick);
				if (previous.distanceToSqr(current) > 1.0E-4) {
					drawArc(buffer, pose, previous, current, camera, 5,
							0.08F + i * 0.025F,
							snake.getId() * 211L + tick * 23L + i,
							Palette.WHITE_GOLD, intensity);
				}
				if (i < 5) {
					drawArc(buffer, pose, current, snake.getCombatPartPosition(i + 1, partialTick),
							camera, 4, 0.06F, snake.getId() * 251L + tick * 29L + i,
							Palette.WHITE_GOLD, intensity * 0.72F);
				}
			}
			return true;
		}
		return false;
	}

	private static boolean isBiteFrame(VirtualSnakeKing snake, int attack, int tick) {
		if (attack == VirtualSnakeKing.ATTACK_BITE) {
			return tick >= 6 && tick <= 15;
		}
		return attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT
				&& snake.getHuntPattern().move(snake.getComboSegment()) == SnakeHuntPattern.Move.BITE
				&& snake.getComboLocalTick() >= 6 && snake.getComboLocalTick() <= 15;
	}

	private static float biteLocalTick(VirtualSnakeKing snake, int attack, int tick) {
		return attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT ? snake.getComboLocalTick() : tick;
	}

	private static boolean isTailFrame(VirtualSnakeKing snake, int attack, int tick) {
		if (attack == VirtualSnakeKing.ATTACK_TAIL_SWEEP) {
			return tick >= 8 && tick <= 23;
		}
		return attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT
				&& snake.getHuntPattern().move(snake.getComboSegment()) == SnakeHuntPattern.Move.TAIL
				&& snake.getComboLocalTick() >= 8 && snake.getComboLocalTick() <= 21;
	}

	private static float tailIntensity(VirtualSnakeKing snake, int attack, int tick) {
		int local = attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT ? snake.getComboLocalTick() : tick;
		return 0.42F + 0.58F * Mth.clamp(
				1.0F - Math.abs(local - SnakeHuntPattern.TAIL_HIT_TICK) / 10.0F, 0.0F, 1.0F);
	}

	private static Vec3 lockedForward(VirtualSnakeKing snake) {
		double yaw = Math.toRadians(snake.getLockedAttackYaw());
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	private static void drawArc(BufferBuilder buffer, Matrix4f pose, Vec3 from, Vec3 to,
			Vec3 camera, int segments, float spread, long seed, Palette palette, float alpha) {
		List<Vec3> points = arcPoints(from, to, segments, spread, seed);
		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3 a = points.get(i);
			Vec3 b = points.get(i + 1);
			addBillboard(buffer, pose, a, b, camera, 0.16F,
					palette.glowR, palette.glowG, palette.glowB, alpha * 0.34F);
			addBillboard(buffer, pose, a, b, camera, 0.075F,
					palette.midR, palette.midG, palette.midB, alpha * 0.68F);
			addBillboard(buffer, pose, a, b, camera, 0.026F,
					palette.coreR, palette.coreG, palette.coreB, alpha);
		}
	}

	private static List<Vec3> arcPoints(Vec3 from, Vec3 to, int segments, float spread, long seed) {
		Vec3 axis = to.subtract(from);
		if (axis.lengthSqr() < 1.0E-6) {
			return List.of(from, to);
		}
		Vec3 direction = axis.normalize();
		Vec3 reference = Math.abs(direction.y) > 0.92
				? new Vec3(1.0, 0.0, 0.0)
				: new Vec3(0.0, 1.0, 0.0);
		Vec3 sideA = direction.cross(reference).normalize();
		Vec3 sideB = direction.cross(sideA).normalize();
		Random random = new Random(seed);
		List<Vec3> points = new ArrayList<>(segments + 1);
		points.add(from);
		for (int i = 1; i < segments; i++) {
			double t = i / (double)segments;
			double attenuation = 4.0 * t * (1.0 - t);
			double a = (random.nextDouble() * 2.0 - 1.0) * spread * attenuation;
			double b = (random.nextDouble() * 2.0 - 1.0) * spread * attenuation;
			points.add(from.add(axis.scale(t)).add(sideA.scale(a)).add(sideB.scale(b)));
		}
		points.add(to);
		return points;
	}

	private static void addBillboard(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b,
			Vec3 camera, float width, float red, float green, float blue, float alpha) {
		Vec3 axis = b.subtract(a);
		if (axis.lengthSqr() < 1.0E-7) {
			return;
		}
		Vec3 midpoint = a.add(b).scale(0.5);
		Vec3 side = axis.normalize().cross(camera.subtract(midpoint));
		if (side.lengthSqr() < 1.0E-7) {
			side = axis.normalize().cross(new Vec3(0.0, 1.0, 0.0));
		}
		side = side.normalize().scale(width);
		vertex(buffer, pose, a.add(side), red, green, blue, alpha);
		vertex(buffer, pose, a.subtract(side), red, green, blue, alpha);
		vertex(buffer, pose, b.subtract(side), red, green, blue, alpha);
		vertex(buffer, pose, b.add(side), red, green, blue, alpha);
	}

	private static void vertex(BufferBuilder buffer, Matrix4f pose, Vec3 point,
			float red, float green, float blue, float alpha) {
		buffer.addVertex(pose, (float)point.x, (float)point.y, (float)point.z)
				.setColor(red, green, blue, alpha);
	}

	private record Palette(float coreR, float coreG, float coreB,
			float midR, float midG, float midB,
			float glowR, float glowG, float glowB) {
		private static final Palette WHITE_GOLD = new Palette(
				1.0F, 1.0F, 0.96F, 1.0F, 0.77F, 0.24F, 0.42F, 0.20F, 0.03F);
	}
}
