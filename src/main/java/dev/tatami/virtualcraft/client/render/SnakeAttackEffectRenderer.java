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
import dev.tatami.virtualcraft.boss.SnakeDashRules;
import dev.tatami.virtualcraft.boss.SnakeHuntPattern;
import dev.tatami.virtualcraft.boss.SnakeKingAttackRules;
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
			drawEnergyRibbon(buffer, pose, head.add(side.scale(0.52)), focus.add(side.scale(0.18)),
					camera, 6, 0.16F, snake.getId() * 109L + tick * 17L, bite, 0.55F + impact * 0.45F);
			drawEnergyRibbon(buffer, pose, head.add(side.scale(-0.52)), focus.add(side.scale(-0.18)),
					camera, 6, 0.16F, snake.getId() * 149L + tick * 19L, bite, 0.55F + impact * 0.45F);
			return true;
		}
		if (isTailFrame(snake, attack, tick)) {
			float intensity = tailIntensity(snake, attack, tick);
			for (int i = 3; i < 6; i++) {
				Vec3 previous = snake.getPreviousCombatPartPosition(i);
				Vec3 current = snake.getCombatPartPosition(i, partialTick);
				if (previous.distanceToSqr(current) > 1.0E-4) {
					drawEnergyRibbon(buffer, pose, previous, current, camera, 5,
							0.08F + i * 0.025F,
							snake.getId() * 211L + tick * 23L + i,
							Palette.WHITE_GOLD, intensity, 1.25F + (i - 3) * 0.22F);
				}
				if (i < 5) {
					drawEnergyRibbon(buffer, pose, current, snake.getCombatPartPosition(i + 1, partialTick),
							camera, 4, 0.06F, snake.getId() * 251L + tick * 29L + i,
							Palette.WHITE_GOLD, intensity * 0.76F, 1.18F + (i - 3) * 0.18F);
				}
			}
			return true;
		}
		if (attack == VirtualSnakeKing.ATTACK_DATA_FANGS
				&& tick >= SnakeKingAttackRules.HIGH_POISON_HIT_TICK - 4
				&& tick <= SnakeKingAttackRules.HIGH_POISON_HIT_TICK + 3) {
			return renderHighPoisonRelease(buffer, pose, snake, camera, partialTick, tick);
		}
		if (attack == VirtualSnakeKing.ATTACK_RUNE_SIGIL
				&& tick >= SnakeKingAttackRules.GROUND_POISON_HIT_TICK - 4
				&& tick <= SnakeKingAttackRules.GROUND_POISON_HIT_TICK + 3) {
			return renderGroundPoisonRelease(buffer, pose, snake, camera, partialTick, tick);
		}
		if (attack == VirtualSnakeKing.ATTACK_CROWN_CHAIN
				&& tick >= SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK - 3
				&& tick <= SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK + 4) {
			return renderCrownChainRelease(buffer, pose, snake, camera, partialTick, tick);
		}
		if (attack == VirtualSnakeKing.ATTACK_CORROSIVE_COIL
				&& tick >= SnakeKingAttackRules.COIL_RELEASE_START - 2
				&& tick <= SnakeKingAttackRules.COIL_RELEASE_END + 3) {
			return renderCoilRelease(buffer, pose, snake, camera, tick);
		}
		if (attack == VirtualSnakeKing.ATTACK_OVERHEAD_FANG
				&& tick >= SnakeKingAttackRules.OVERHEAD_HIT_TICK - 3
				&& tick <= SnakeKingAttackRules.OVERHEAD_HIT_TICK + 4) {
			return renderOverheadRelease(buffer, pose, snake, camera, partialTick, tick);
		}
		if (attack == VirtualSnakeKing.ATTACK_VOID_DASH
				&& ((tick >= SnakeDashRules.FIRST_START - 2 && tick <= SnakeDashRules.FIRST_START + 4)
						|| (tick >= SnakeDashRules.SECOND_START - 2
								&& tick <= SnakeDashRules.SECOND_START + 4))) {
			return renderDashRelease(buffer, pose, snake, camera, partialTick, tick);
		}
		if (attack == VirtualSnakeKing.ATTACK_CROWN_JUDGMENT && tick >= 58 && tick <= 66) {
			return renderJudgmentRelease(buffer, pose, snake, camera, partialTick, tick);
		}
		return false;
	}

	private static boolean renderHighPoisonRelease(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, float partialTick, int tick) {
		Vec3 head = snake.getCombatPartPosition(0, partialTick).add(0.0, 0.24, 0.0);
		Vec3 forward = lockedForward(snake);
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		double length = 8.5;
		float pulse = impactEnvelope(tick, SnakeKingAttackRules.HIGH_POISON_HIT_TICK, 4);
		for (int rail = -1; rail <= 1; rail++) {
			Vec3 target = snake.position().add(forward.scale(length))
					.add(side.scale(rail * 1.05)).add(0.0, snake.getLockedTargetY() - snake.getY(), 0.0);
			drawEnergyRibbon(buffer, pose, head.add(side.scale(rail * 0.34)), target, camera,
					9, 0.22F, snake.getId() * 313L + rail * 41L, Palette.CYAN_VENOM,
					0.38F + pulse * 0.52F, rail == 0 ? 1.0F : 0.72F);
		}
		return true;
	}

	private static boolean renderGroundPoisonRelease(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, float partialTick, int tick) {
		Vec3 origin = snake.getCombatPartPosition(0, partialTick).add(0.0, -0.25, 0.0);
		Vec3 forward = lockedForward(snake);
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		float pulse = impactEnvelope(tick, SnakeKingAttackRules.GROUND_POISON_HIT_TICK, 4);
		for (int i = -3; i <= 3; i++) {
			double spread = i / 3.0;
			Vec3 direction = forward.scale(1.0 - Math.abs(spread) * 0.18)
					.add(side.scale(spread * 0.72)).normalize();
			Vec3 target = snake.position().add(direction.scale(7.2)).add(0.0, 0.10, 0.0);
			drawEnergyRibbon(buffer, pose, origin, target, camera, 7, 0.10F,
					snake.getId() * 367L + i * 53L, Palette.RED_VENOM,
					(0.26F + pulse * 0.52F) * (1.0F - Math.abs((float)spread) * 0.18F), 0.74F);
		}
		return true;
	}

	private static boolean renderCrownChainRelease(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, float partialTick, int tick) {
		Vec3 crown = snake.getCombatPartPosition(0, partialTick).add(0.0, 2.25, 0.0);
		float pulse = impactEnvelope(tick, SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK, 4);
		for (int index = 0; index < 8; index++) {
			double angle = Mth.TWO_PI * index / 8.0 + tick * 0.018;
			Vec3 target = snake.position().add(
					Math.cos(angle) * 15.5, 0.12, Math.sin(angle) * 15.5);
			drawArc(buffer, pose, crown, target, camera, 11, 0.24F,
					snake.getId() * 397L + index * 61L,
					Palette.CYAN_VENOM, 0.16F + pulse * 0.42F, 0.58F);
		}
		return true;
	}

	private static boolean renderCoilRelease(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, int tick) {
		double radius = SnakeKingAttackRules.coilRadius(tick);
		float pulse = tick <= SnakeKingAttackRules.COIL_RELEASE_END
				? 0.9F : impactEnvelope(tick, SnakeKingAttackRules.COIL_RELEASE_END, 4);
		int segments = 28;
		for (int index = 0; index < segments; index++) {
			double angleA = Mth.TWO_PI * index / segments;
			double angleB = Mth.TWO_PI * (index + 1) / segments;
			Vec3 lowA = snake.position().add(Math.cos(angleA) * radius, 0.12,
					Math.sin(angleA) * radius);
			Vec3 lowB = snake.position().add(Math.cos(angleB) * radius, 0.12,
					Math.sin(angleB) * radius);
			Vec3 highA = lowA.add(0.0, 0.62, 0.0);
			Vec3 highB = lowB.add(0.0, 0.62, 0.0);
			drawEnergyRibbon(buffer, pose, lowA, lowB, camera, 2, 0.01F,
					snake.getId() * 439L + index * 47L, Palette.JUDGMENT,
					0.42F + pulse * 0.48F, 1.22F);
			drawEnergyRibbon(buffer, pose, highA, highB, camera, 2, 0.01F,
					snake.getId() * 443L + index * 53L, Palette.JUDGMENT,
					0.24F + pulse * 0.38F, 0.72F);
			if ((index & 3) == 0) {
				drawEnergyRibbon(buffer, pose, lowA, highA, camera, 2, 0.01F,
						snake.getId() * 449L + index * 59L, Palette.JUDGMENT,
						0.28F + pulse * 0.42F, 0.78F);
			}
		}
		return true;
	}

	private static boolean renderOverheadRelease(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, float partialTick, int tick) {
		Vec3 head = snake.getCombatPartPosition(0, partialTick).add(0.0, 0.45, 0.0);
		Vec3 forward = lockedForward(snake);
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 impact = snake.position().add(forward.scale(4.5)).add(0.0, 0.18, 0.0);
		float pulse = impactEnvelope(tick, SnakeKingAttackRules.OVERHEAD_HIT_TICK, 4);
		drawEnergyRibbon(buffer, pose, head.add(side.scale(0.55)), impact, camera,
				7, 0.09F, snake.getId() * 461L + tick,
				Palette.CYAN_VENOM, 0.3F + pulse * 0.54F, 1.05F);
		drawEnergyRibbon(buffer, pose, head.add(side.scale(-0.55)), impact, camera,
				7, 0.09F, snake.getId() * 463L + tick,
				Palette.WHITE_GOLD, 0.3F + pulse * 0.54F, 1.05F);
		return true;
	}

	private static boolean renderDashRelease(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, float partialTick, int tick) {
		int impactTick = tick < (SnakeDashRules.FIRST_START + SnakeDashRules.SECOND_START) / 2
				? SnakeDashRules.FIRST_START : SnakeDashRules.SECOND_START;
		float pulse = Math.max(0.3F, impactEnvelope(tick, impactTick, 5));
		for (int i = 0; i < 6; i++) {
			Vec3 previous = snake.getPreviousCombatPartPosition(i);
			Vec3 current = snake.getCombatPartPosition(i, partialTick);
			Vec3 wake = previous.add(lockedForward(snake).scale(-1.4 - i * 0.18));
			drawEnergyRibbon(buffer, pose, current, wake, camera, 4, 0.07F,
					snake.getId() * 421L + i * 67L + tick, i < 2 ? Palette.WHITE_GOLD : Palette.VOID_CYAN,
					0.34F + pulse * 0.44F, 0.65F + i * 0.08F);
		}
		return true;
	}

	private static boolean renderJudgmentRelease(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, Vec3 camera, float partialTick, int tick) {
		Vec3 crown = snake.getCombatPartPosition(0, partialTick).add(0.0, 2.2, 0.0);
		float pulse = impactEnvelope(tick, 62, 5);
		for (int i = 0; i < 8; i++) {
			if (i == snake.getJudgmentSafePath()) {
				continue;
			}
			double angle = Mth.TWO_PI * i / 8.0;
			Vec3 target = snake.position().add(Math.cos(angle) * 9.0, 0.12, Math.sin(angle) * 9.0);
			drawArc(buffer, pose, crown, target, camera, 10, 0.30F,
					snake.getId() * 487L + i * 71L, Palette.JUDGMENT,
					0.24F + pulse * 0.58F, 0.68F);
		}
		return true;
	}

	private static float impactEnvelope(int tick, int impactTick, int radius) {
		return Mth.clamp(1.0F - Math.abs(tick - impactTick) / (float)Math.max(1, radius), 0.0F, 1.0F);
	}

	private static boolean isBiteFrame(VirtualSnakeKing snake, int attack, int tick) {
		if (attack == VirtualSnakeKing.ATTACK_BITE) {
			return tick >= 6 && tick <= SnakeHuntPattern.BITE_HIT_TICK + 3;
		}
		return attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT
				&& snake.isComboActiveSegment()
				&& snake.getHuntPattern().move(snake.getComboSegment()) == SnakeHuntPattern.Move.BITE
				&& snake.getComboLocalTick() >= 6
				&& snake.getComboLocalTick() <= SnakeHuntPattern.BITE_HIT_TICK + 3;
	}

	private static float biteLocalTick(VirtualSnakeKing snake, int attack, int tick) {
		return attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT ? snake.getComboLocalTick() : tick;
	}

	private static boolean isTailFrame(VirtualSnakeKing snake, int attack, int tick) {
		if (attack == VirtualSnakeKing.ATTACK_TAIL_SWEEP) {
			return tick >= 8 && tick <= SnakeHuntPattern.TAIL_HIT_TICK + 3;
		}
		return attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT
				&& snake.isComboActiveSegment()
				&& snake.getHuntPattern().move(snake.getComboSegment()) == SnakeHuntPattern.Move.TAIL
				&& snake.getComboLocalTick() >= 8
				&& snake.getComboLocalTick() <= SnakeHuntPattern.TAIL_HIT_TICK + 3;
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
		drawArc(buffer, pose, from, to, camera, segments, spread, seed, palette, alpha, 1.0F);
	}

	private static void drawEnergyRibbon(BufferBuilder buffer, Matrix4f pose, Vec3 from, Vec3 to,
			Vec3 camera, int segments, float spread, long seed, Palette palette, float alpha) {
		drawEnergyRibbon(buffer, pose, from, to, camera, segments, spread, seed, palette, alpha, 1.0F);
	}

	private static void drawEnergyRibbon(BufferBuilder buffer, Matrix4f pose, Vec3 from, Vec3 to,
			Vec3 camera, int segments, float spread, long seed, Palette palette, float alpha,
			float widthScale) {
		Vec3 axis = to.subtract(from);
		if (axis.lengthSqr() < 1.0E-7) {
			return;
		}
		Vec3 direction = axis.normalize();
		Vec3 reference = Math.abs(direction.y) > 0.92
				? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 bend = direction.cross(reference).normalize().scale(spread * 0.35);
		Vec3 previous = from;
		for (int i = 1; i <= Math.max(2, segments); i++) {
			double progress = i / (double)Math.max(2, segments);
			double curve = Math.sin(progress * Math.PI) * (seed % 2 == 0 ? 1.0 : -1.0);
			Vec3 next = from.add(axis.scale(progress)).add(bend.scale(curve));
			addBillboard(buffer, pose, previous, next, camera, 0.18F * widthScale,
					palette.glowR, palette.glowG, palette.glowB, alpha * 0.30F);
			addBillboard(buffer, pose, previous, next, camera, 0.08F * widthScale,
					palette.midR, palette.midG, palette.midB, alpha * 0.72F);
			addBillboard(buffer, pose, previous, next, camera, 0.028F * widthScale,
					palette.coreR, palette.coreG, palette.coreB, alpha);
			previous = next;
		}
	}

	private static void drawArc(BufferBuilder buffer, Matrix4f pose, Vec3 from, Vec3 to,
			Vec3 camera, int segments, float spread, long seed, Palette palette, float alpha,
			float widthScale) {
		List<Vec3> points = arcPoints(from, to, segments, spread, seed);
		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3 a = points.get(i);
			Vec3 b = points.get(i + 1);
			addBillboard(buffer, pose, a, b, camera, 0.16F * widthScale,
					palette.glowR, palette.glowG, palette.glowB, alpha * 0.34F);
			addBillboard(buffer, pose, a, b, camera, 0.075F * widthScale,
					palette.midR, palette.midG, palette.midB, alpha * 0.68F);
			addBillboard(buffer, pose, a, b, camera, 0.026F * widthScale,
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
		double cameraDistance = midpoint.distanceTo(camera);
		if (cameraDistance <= 0.45) {
			return;
		}
		alpha *= Mth.clamp((float)((cameraDistance - 0.45) / 1.15), 0.0F, 1.0F);
		if (alpha <= 0.015F) {
			return;
		}
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
		private static final Palette CYAN_VENOM = new Palette(
				0.88F, 1.0F, 1.0F, 0.12F, 0.72F, 0.92F, 0.02F, 0.14F, 0.28F);
		private static final Palette RED_VENOM = new Palette(
				1.0F, 0.94F, 0.72F, 0.94F, 0.22F, 0.12F, 0.28F, 0.02F, 0.04F);
		private static final Palette VOID_CYAN = new Palette(
				0.84F, 1.0F, 1.0F, 0.16F, 0.64F, 0.76F, 0.02F, 0.10F, 0.14F);
		private static final Palette JUDGMENT = new Palette(
				1.0F, 0.98F, 0.84F, 0.95F, 0.58F, 0.12F, 0.25F, 0.04F, 0.02F);
	}
}
