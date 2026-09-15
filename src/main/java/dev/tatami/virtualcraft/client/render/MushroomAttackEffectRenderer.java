package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tatami.virtualcraft.boss.MushroomAttackVolume;
import dev.tatami.virtualcraft.boss.MushroomComboPattern;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Release geometry that appears around impact while telegraphs remain independently visible. */
public final class MushroomAttackEffectRenderer {
	private static final Palette MID = new Palette(0xD5EEEA, 0x6F9995, 0x233536);
	private static final Palette HIGH = new Palette(0xF5FDFF, 0x38BFFF, 0x0A3147);
	private static final Palette LOW = new Palette(0xFFF9D7, 0xFFC247, 0x4A3310);
	private static final Palette SPORE = new Palette(0xFFF4E6, 0xB9778D, 0x3A2630);
	private static final Palette ULTIMATE_ROOT = new Palette(0xF5E8C2, 0x4DBAA4, 0x173C38);
	private static final Palette ULTIMATE_CAP = new Palette(0xFFF1CC, 0xC16E86, 0x4A2634);

	private MushroomAttackEffectRenderer() {
	}

	public static void render(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		// Alpha blending keeps dark silhouettes readable on the bright virtual terrain.
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
				DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = poseStack.last().pose();
		boolean wrote = false;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof VirtualMushroomKing boss
					&& (boss.isAlive() || boss.deathTime > 0)) {
				if (boss.isAlive() && boss.getAttackState() != 0) {
					wrote |= renderAttack(buffer, pose, boss, camera);
				}
				wrote |= renderRitual(buffer, pose, boss, camera);
			}
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

	private static boolean renderAttack(BufferBuilder buffer, Matrix4f pose,
			VirtualMushroomKing boss, Vec3 camera) {
		int attack = boss.getAttackState();
		int tick = boss.getAttackTick();
		Vec3 origin = boss.position();
		Vec3 forward = forward(boss.getLockedYaw());
		MushroomComboPattern combo = boss.getComboPattern();
		if (combo != null) {
			// Combo readability comes from the animated body, physical root/spore entities,
			// and contact particles. Generic ribbons and crosses hid those silhouettes.
			return false;
		}
		return switch (attack) {
			case MushroomKingAttackRules.ROOT_CADENCE ->
				renderRootCadence(buffer, pose, origin, forward, camera, tick);
			case MushroomKingAttackRules.STEM_HAMMER ->
				renderStemHammer(buffer, pose, origin, forward, camera, tick);
			case MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY ->
				renderNeedles(buffer, pose, origin, forward, camera, tick);
			case MushroomKingAttackRules.CAP_CLAP ->
				renderCapClap(buffer, pose, origin, forward, camera, tick);
			case MushroomKingAttackRules.CANOPY_CLOSURE ->
				renderCanopyClosure(buffer, pose, origin, forward, camera, tick);
			case MushroomKingAttackRules.SPORE_BREATH ->
				renderSporeBreath(buffer, pose, origin, forward, camera, tick);
			case MushroomKingAttackRules.MYCELIAL_SURGE ->
				renderMycelialSurge(buffer, pose, boss, origin, forward, camera, tick);
			case MushroomKingAttackRules.ROOT_CONVERGENCE ->
				renderRootConvergence(buffer, pose, boss, origin, camera, tick);
			default -> false;
		};
	}

	private static boolean renderRitual(BufferBuilder buffer, Matrix4f pose,
			VirtualMushroomKing boss, Vec3 camera) {
		if (boss.getSummonTicks() > 0) {
			float progress = 1.0F - boss.getSummonTicks()
					/ (float)Math.max(1, VirtualMushroomKing.SUMMON_DURATION);
			Vec3 center = boss.position().add(0.0, 0.06, 0.0);
			for (int layer = 0; layer < 3; layer++) {
				ring(buffer, pose, center, 1.4 + layer * 1.15 + progress,
						0.24 + layer * 0.07, layer == 1 ? HIGH : SPORE,
						0.45F + progress * 0.45F);
			}
			return true;
		}
		if (boss.deathTime > 0) {
			float progress = Mth.clamp(boss.deathTime / 64.0F, 0.0F, 1.0F);
			Vec3 center = boss.position().add(0.0, 0.08, 0.0);
			ring(buffer, pose, center, 1.0 + progress * 7.0, 0.42, SPORE,
					1.0F - progress * 0.55F);
			for (int ray = 0; ray < 6; ray++) {
				double angle = Mth.TWO_PI * ray / 6.0 - progress;
				Vec3 end = center.add(Math.cos(angle) * (2.0 + progress * 5.0),
						1.0 + progress * 3.0, Math.sin(angle) * (2.0 + progress * 5.0));
				energyRibbon(buffer, pose, center.add(0.0, 2.8, 0.0), end, camera,
						0.17 + progress * 0.14, MID, 0.82F - progress * 0.4F);
			}
			return true;
		}
		return false;
	}

	private static boolean renderRootCadence(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, Vec3 camera, int tick) {
		int waveTick = nearestWave(tick, 24, 38, 52);
		float impact = releaseEnvelope(tick, waveTick, 7, 4);
		if (impact <= 0.0F) {
			return false;
		}
		int wave = waveTick == 24 ? 1 : waveTick == 38 ? 2 : 3;
		Vec3 end = origin.add(forward.scale(4.0 + wave * 0.25)).add(0.0, 1.45, 0.0);
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		double handSide = wave == 2 ? -1.0 : 1.0;
		Vec3 source = origin.add(side.scale(handSide * 1.35))
				.add(forward.scale(0.45)).add(0.0, 2.15, 0.0);
		energyRibbon(buffer, pose, source, end, camera, 0.2 + impact * 0.1,
				MID, 0.55F + impact * 0.4F);
		impactCross(buffer, pose, end, camera, 0.7 + impact * 0.45, MID, impact);
		return true;
	}

	private static boolean renderStemHammer(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, Vec3 camera, int tick) {
		float impact = releaseEnvelope(tick, 34, 9, 4);
		if (impact <= 0.0F) {
			return false;
		}
		Vec3 center = origin.add(forward.scale(3.5)).add(0.0, 0.12, 0.0);
		Vec3 hammer = origin.add(forward.scale(0.55)).add(0.0, 4.35, 0.0);
		energyRibbon(buffer, pose, hammer, center.add(0.0, 0.3, 0.0), camera,
				0.26 + impact * 0.16, MID, 0.58F + impact * 0.38F);
		for (int spike = 0; spike < 5; spike++) {
			double angle = Math.atan2(forward.z, forward.x) - 0.62 + spike * 0.31;
			Vec3 base = center.add(Math.cos(angle) * 1.3, 0.0, Math.sin(angle) * 1.3);
			sporeBloom(buffer, pose, base, 0.65 + impact * 1.1, angle, MID, impact);
		}
		radialGroundBurst(buffer, pose, center, camera, 2.8, 10, MID, impact);
		impactCross(buffer, pose, center.add(0.0, 0.35, 0.0), camera,
				0.9 + impact * 0.55, MID, impact);
		return true;
	}

	private static boolean renderNeedles(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, Vec3 camera, int tick) {
		boolean wrote = false;
		int[] impacts = {28, 40, 52};
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		for (int wave = 0; wave < impacts.length; wave++) {
			int startTick = impacts[wave] - 5;
			if (tick < startTick || tick > impacts[wave] + 2) {
				continue;
			}
			float travel = Mth.clamp((tick - startTick) / 5.0F, 0.0F, 1.0F);
			Vec3 start = origin.add(side.scale(wave == 1 ? 0.9 : -0.9)).add(0.0, 2.25, 0.0);
			Vec3 head = start.add(forward.scale(MushroomAttackVolume.NEEDLE_LENGTH * travel));
			Vec3 tail = head.add(forward.scale(-0.85));
			energyRibbon(buffer, pose, tail, head, camera, 0.12, MID, 0.72F + travel * 0.2F);
			wrote = true;
		}
		return wrote;
	}

	private static boolean renderCapClap(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, Vec3 camera, int tick) {
		float impact = releaseEnvelope(tick, 30, 8, 4);
		if (impact <= 0.0F) {
			return false;
		}
		Vec3 target = origin.add(forward.scale(3.4)).add(0.0, 1.8, 0.0);
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 left = origin.add(side.scale(1.65)).add(forward.scale(0.35)).add(0.0, 2.35, 0.0);
		Vec3 right = origin.add(side.scale(-1.65)).add(forward.scale(0.35)).add(0.0, 2.35, 0.0);
		energyRibbon(buffer, pose, left, target.add(side.scale(0.18)), camera,
				0.22 + impact * 0.1, MID, 0.5F + impact * 0.45F);
		energyRibbon(buffer, pose, right, target.add(side.scale(-0.18)), camera,
				0.22 + impact * 0.1, MID, 0.5F + impact * 0.45F);
		for (int rib = -2; rib <= 2; rib++) {
			double offset = rib * 0.34;
			energyRibbon(buffer, pose, left.add(0.0, offset, 0.0),
					right.add(0.0, -offset, 0.0), camera,
					0.07 + Math.abs(rib) * 0.012, MID, 0.28F + impact * 0.46F);
		}
		impactCross(buffer, pose, target, camera, 1.0, MID, impact);
		return true;
	}

	private static boolean renderCanopyClosure(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, Vec3 camera, int tick) {
		float impact = releaseEnvelope(tick, 30, 9, 5);
		if (impact <= 0.0F) {
			return false;
		}
		double center = Math.atan2(forward.z, forward.x);
		Vec3 previous = origin.add(Math.cos(center - 1.25) * 5.6, 2.5,
				Math.sin(center - 1.25) * 5.6);
		for (int segment = 1; segment <= 10; segment++) {
			double angle = center - 1.25 + 2.5 * segment / 10.0;
			Vec3 next = origin.add(Math.cos(angle) * 5.6, 2.5, Math.sin(angle) * 5.6);
			energyRibbon(buffer, pose, previous, next, camera, 0.18 + impact * 0.1,
					HIGH, 0.4F + impact * 0.38F);
			if ((segment & 1) == 0) {
				Vec3 canopy = origin.add(Math.cos(angle) * 1.25, 4.35,
						Math.sin(angle) * 1.25);
				energyRibbon(buffer, pose, canopy, next, camera, 0.08,
						HIGH, 0.3F + impact * 0.44F);
			}
			previous = next;
		}
		impactCross(buffer, pose, origin.add(forward.scale(4.6)).add(0.0, 2.5, 0.0),
				camera, 1.35, HIGH, impact);
		return true;
	}

	private static boolean renderSporeBreath(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, Vec3 camera, int tick) {
		int waveTick = nearestWave(tick, 26, 33, 40);
		float impact = releaseEnvelope(tick, waveTick, 5, 4);
		if (impact <= 0.0F) {
			return false;
		}
		int wave = waveTick == 26 ? 1 : waveTick == 33 ? 2 : 3;
		Vec3 direction = forward;
		Vec3 mouth = origin.add(0.0, 2.75, 0.0).add(direction.scale(0.8));
		double angle = Math.atan2(direction.z, direction.x);
		for (int petal = 0; petal < 6; petal++) {
			double petalAngle = angle + Mth.TWO_PI * petal / 6.0;
			Vec3 base = mouth.add(Math.cos(petalAngle) * 0.45,
					Math.sin(petal * 2.1) * 0.18,
					Math.sin(petalAngle) * 0.45);
			sporeBloom(buffer, pose, base, 0.38 + impact * 0.68,
					petalAngle, HIGH, 0.48F + impact * 0.48F);
		}
		ring(buffer, pose, mouth, 0.52 + impact * 0.72,
				0.24, HIGH, 0.42F + impact * 0.5F);
		impactCross(buffer, pose, mouth.add(direction.scale(0.6)),
				camera, 0.58 + impact * 0.48, HIGH, impact);
		return true;
	}

	private static boolean renderMycelialSurge(BufferBuilder buffer, Matrix4f pose,
			VirtualMushroomKing boss, Vec3 origin, Vec3 forward, Vec3 camera, int tick) {
		int waveTick = nearestWave(tick, 28, 36, 44);
		float impact = releaseEnvelope(tick, waveTick, 4, 4);
		if (impact <= 0.0F) {
			return false;
		}
		int wave = waveTick == 28 ? 1 : waveTick == 36 ? 2 : 3;
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 lane = origin.add(forward.scale(MushroomAttackVolume.surgeStart(wave)));
		Vec3 stomp = lane.add(0.0, 0.06, 0.0);
		double angle = Math.atan2(forward.z, forward.x);
		ring(buffer, pose, stomp, 0.65 + impact * 0.55, 0.2, LOW,
				0.44F + impact * 0.5F);
		radialGroundBurst(buffer, pose, stomp, camera, 2.5 + wave * 0.35,
				10, LOW, impact);
		for (int spike = 1; spike <= 5; spike++) {
			Vec3 base = lane.add(forward.scale(
					MushroomAttackVolume.surgeLength(wave) * spike / 5.0)).add(0.0, 0.04, 0.0);
			sporeBloom(buffer, pose, base, 0.45 + impact * (0.5 + spike * 0.06),
					angle, LOW, impact);
			double branchSide = (spike & 1) == 0 ? 0.72 : -0.72;
			Vec3 branch = base.add(side.scale(branchSide))
					.add(forward.scale(-0.34)).add(0.0, 0.14 + impact * 0.22, 0.0);
			sporeBloom(buffer, pose, branch, 0.28 + impact * 0.52,
					angle + (branchSide > 0.0 ? 0.65 : -0.65), LOW, impact * 0.82F);
		}
		Vec3 front = lane.add(forward.scale(MushroomAttackVolume.surgeLength(wave)));
		radialGroundBurst(buffer, pose, front, camera, 2.0, 8, LOW, impact);
		return true;
	}

	private static boolean renderRootConvergence(BufferBuilder buffer, Matrix4f pose,
			VirtualMushroomKing boss, Vec3 origin, Vec3 camera, int tick) {
		int impactTick = MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.ROOT_CONVERGENCE, 1);
		float charge = Mth.clamp(tick / (float)impactTick, 0.0F, 1.0F);
		float release = releaseEnvelope(tick, impactTick, 7, 10);
		double rotation = boss.getAttackVariant() * 0.37 + tick * 0.014;
		for (int pod = 0; pod < 8; pod++) {
			double angle = rotation + Mth.TWO_PI * pod / 8.0;
			double radius = 2.2 + (pod & 1) * 0.55;
			Vec3 base = origin.add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
			sporeBloom(buffer, pose, base,
					0.34 + charge * (0.7 + (pod & 1) * 0.18), angle,
					(pod & 1) == 0 ? ULTIMATE_ROOT : ULTIMATE_CAP,
					0.28F + charge * 0.62F);
		}
		if (release > 0.0F) {
			Vec3 center = origin.add(0.0, 0.08, 0.0);
			for (int layer = 0; layer < 3; layer++) {
				double radius = 3.0 + layer * 6.8 + release * (2.0 + layer * 1.2);
				ring(buffer, pose, center.add(0.0, layer * 0.025, 0.0), radius,
						0.28 + layer * 0.12,
						(layer & 1) == 0 ? ULTIMATE_ROOT : ULTIMATE_CAP,
						release * (0.76F - layer * 0.08F));
			}
			radialGroundBurst(buffer, pose, center, camera,
					MushroomAttackVolume.CONVERGENCE_ARENA_RADIUS,
					24, ULTIMATE_ROOT, release * 0.86F);
			impactCross(buffer, pose, origin.add(0.0, 2.7, 0.0), camera,
					2.1 + release * 1.3, ULTIMATE_CAP, release * 0.82F);
		}
		return true;
	}

	private static int nearestWave(int tick, int... waves) {
		int best = waves[0];
		for (int wave : waves) {
			if (Math.abs(tick - wave) < Math.abs(tick - best)) {
				best = wave;
			}
		}
		return best;
	}

	private static float envelope(int tick, int center, int radius) {
		return Mth.clamp(1.0F - Math.abs(tick - center) / (float)Math.max(1, radius), 0.0F, 1.0F);
	}

	private static float releaseEnvelope(int tick, int impactTick, int windup, int fade) {
		if (tick < impactTick - windup || tick > impactTick + fade) {
			return 0.0F;
		}
		if (tick <= impactTick) {
			float progress = (tick - (impactTick - windup)) / (float)Math.max(1, windup);
			return 0.22F + Mth.clamp(progress, 0.0F, 1.0F) * 0.78F;
		}
		return Mth.clamp(1.0F - (tick - impactTick) / (float)Math.max(1, fade), 0.0F, 1.0F);
	}

	private static Vec3 forward(float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	private static Vec3 rotateY(Vec3 direction, double degrees) {
		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(direction.x * cos - direction.z * sin, 0.0,
				direction.x * sin + direction.z * cos);
	}

	private static void energyRibbon(BufferBuilder buffer, Matrix4f pose, Vec3 from, Vec3 to,
			Vec3 camera, double width, Palette palette, float alpha) {
		Vec3 axis = to.subtract(from);
		if (axis.lengthSqr() < 1.0E-7) {
			return;
		}
		int segments = Mth.clamp((int)Math.ceil(axis.length() * 1.2), 4, 12);
		long seed = Double.doubleToLongBits(from.x * 31.0 + from.y * 17.0 + from.z * 13.0)
				^ Double.doubleToLongBits(to.x * 19.0 + to.y * 23.0 + to.z * 29.0);
		List<Vec3> points = curvedPoints(from, to, segments,
				(float)Math.min(0.34, width * 0.72), seed);
		for (int index = 0; index + 1 < points.size(); index++) {
			Vec3 a = points.get(index);
			Vec3 b = points.get(index + 1);
			Vec3 midpoint = a.add(b).scale(0.5);
			Vec3 side = b.subtract(a).normalize().cross(camera.subtract(midpoint));
			if (side.lengthSqr() < 1.0E-7) {
				side = b.subtract(a).normalize().cross(new Vec3(0.0, 1.0, 0.0));
			}
			side = side.normalize();
			billboard(buffer, pose, a, b, side, width, palette.dark(), alpha * 0.42F);
			billboard(buffer, pose, a, b, side, width * 0.5, palette.main(), alpha * 0.76F);
			billboard(buffer, pose, a, b, side, width * 0.14, palette.core(), alpha);
		}
	}

	private static void impactCross(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			Vec3 camera, double radius, Palette palette, float alpha) {
		Vec3 view = camera.subtract(center).normalize();
		Vec3 side = view.cross(new Vec3(0.0, 1.0, 0.0));
		if (side.lengthSqr() < 1.0E-7) {
			side = new Vec3(1.0, 0.0, 0.0);
		}
		side = side.normalize();
		Vec3 up = side.cross(view).normalize();
		int segments = 20;
		double outerRadius = radius;
		double innerRadius = radius * 0.68;
		for (int segment = 0; segment < segments; segment++) {
			double a0 = Mth.TWO_PI * segment / segments;
			double a1 = Mth.TWO_PI * (segment + 1) / segments;
			Vec3 inner0 = center.add(side.scale(Math.cos(a0) * innerRadius))
					.add(up.scale(Math.sin(a0) * innerRadius));
			Vec3 outer0 = center.add(side.scale(Math.cos(a0) * outerRadius))
					.add(up.scale(Math.sin(a0) * outerRadius));
			Vec3 outer1 = center.add(side.scale(Math.cos(a1) * outerRadius))
					.add(up.scale(Math.sin(a1) * outerRadius));
			Vec3 inner1 = center.add(side.scale(Math.cos(a1) * innerRadius))
					.add(up.scale(Math.sin(a1) * innerRadius));
			int color = segment % 5 == 0 ? palette.core()
					: (segment & 1) == 0 ? palette.main() : palette.dark();
			quad(buffer, pose, inner0, outer0, outer1, inner1, color,
					alpha * (segment % 5 == 0 ? 0.94F : 0.72F));
		}
		ring(buffer, pose, center.add(0.0, -0.08, 0.0),
				radius * 0.72, Math.max(0.08, radius * 0.12), palette, alpha * 0.56F);
	}

	private static void radialGroundBurst(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			Vec3 camera, double radius, int rays, Palette palette, float alpha) {
		for (int ray = 0; ray < rays; ray++) {
			double angle = Mth.TWO_PI * ray / rays + ((ray & 1) == 0 ? 0.08 : -0.08);
			Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			Vec3 side = new Vec3(-direction.z, 0.0, direction.x);
			Vec3 start = center.add(direction.scale(0.28)).add(0.0, 0.04, 0.0);
			Vec3 end = center.add(direction.scale(radius * (0.72 + (ray % 3) * 0.12)))
					.add(0.0, 0.08 + (ray & 1) * 0.08, 0.0);
			double width = 0.10 + (ray % 3) * 0.025;
			Vec3 shoulder = start.lerp(end, 0.58);
			groundBranch(buffer, pose, start, shoulder, side, width * 0.25, width,
					palette.dark(), alpha * 0.7F);
			groundBranch(buffer, pose, shoulder, end, side, width, width * 0.42,
					palette.dark(), alpha * 0.7F);
			groundBranch(buffer, pose, start.add(0.0, 0.012, 0.0),
					shoulder.add(0.0, 0.012, 0.0), side, width * 0.08, width * 0.34,
					palette.core(), alpha * 0.9F);
			groundBranch(buffer, pose, shoulder.add(0.0, 0.012, 0.0),
					end.add(0.0, 0.012, 0.0), side, width * 0.34, width * 0.14,
					palette.core(), alpha * 0.9F);
		}
	}

	private static void groundBranch(BufferBuilder buffer, Matrix4f pose, Vec3 start, Vec3 end,
			Vec3 side, double startWidth, double endWidth, int color, float alpha) {
		quad(buffer, pose, start.add(side.scale(startWidth)), end.add(side.scale(endWidth)),
				end.add(side.scale(-endWidth)), start.add(side.scale(-startWidth)), color, alpha);
	}

	private static List<Vec3> curvedPoints(Vec3 from, Vec3 to, int segments,
			float spread, long seed) {
		Vec3 axis = to.subtract(from);
		Vec3 direction = axis.normalize();
		Vec3 reference = Math.abs(direction.y) > 0.92
				? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 sideA = direction.cross(reference).normalize();
		Vec3 sideB = direction.cross(sideA).normalize();
		Random random = new Random(seed);
		List<Vec3> points = new ArrayList<>(segments + 1);
		points.add(from);
		for (int index = 1; index < segments; index++) {
			double progress = index / (double)segments;
			double attenuation = 4.0 * progress * (1.0 - progress);
			double offsetA = (random.nextDouble() * 2.0 - 1.0) * spread * attenuation;
			double offsetB = (random.nextDouble() * 2.0 - 1.0) * spread * 0.6 * attenuation;
			points.add(from.add(axis.scale(progress))
					.add(sideA.scale(offsetA)).add(sideB.scale(offsetB)));
		}
		points.add(to);
		return points;
	}

	private static void ring(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			double radius, double width, Palette palette, float alpha) {
		int segments = 48;
		for (int i = 0; i < segments; i++) {
			double a0 = Mth.TWO_PI * i / segments;
			double a1 = Mth.TWO_PI * (i + 1) / segments;
			Vec3 i0 = center.add(Math.cos(a0) * (radius - width), 0.0,
					Math.sin(a0) * (radius - width));
			Vec3 i1 = center.add(Math.cos(a1) * (radius - width), 0.0,
					Math.sin(a1) * (radius - width));
			Vec3 o1 = center.add(Math.cos(a1) * radius, 0.0, Math.sin(a1) * radius);
			Vec3 o0 = center.add(Math.cos(a0) * radius, 0.0, Math.sin(a0) * radius);
			quad(buffer, pose, i0, i1, o1, o0, palette.dark(), alpha * 0.45F);
			quad(buffer, pose,
					center.add(Math.cos(a0) * (radius - width * 0.45), 0.015,
							Math.sin(a0) * (radius - width * 0.45)),
					center.add(Math.cos(a1) * (radius - width * 0.45), 0.015,
							Math.sin(a1) * (radius - width * 0.45)),
					o1.add(0.0, 0.015, 0.0), o0.add(0.0, 0.015, 0.0),
					palette.main(), alpha * 0.82F);
			quad(buffer, pose,
					center.add(Math.cos(a0) * (radius - width * 0.18), 0.024,
							Math.sin(a0) * (radius - width * 0.18)),
					center.add(Math.cos(a1) * (radius - width * 0.18), 0.024,
							Math.sin(a1) * (radius - width * 0.18)),
					center.add(Math.cos(a1) * radius, 0.024, Math.sin(a1) * radius),
					center.add(Math.cos(a0) * radius, 0.024, Math.sin(a0) * radius),
					palette.core(), alpha * 0.76F);
		}
	}

	private static void sporeBloom(BufferBuilder buffer, Matrix4f pose, Vec3 base,
			double height, double angle, Palette palette, float alpha) {
		Vec3 side = new Vec3(-Math.sin(angle), 0.0, Math.cos(angle));
		Vec3 forward = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		double stemHeight = height * 0.58;
		double stemWidth = 0.07 + height * 0.018;
		Vec3 stemTop = base.add(0.0, stemHeight, 0.0);
		quad(buffer, pose, base.add(side.scale(stemWidth)), base.subtract(side.scale(stemWidth)),
				stemTop.subtract(side.scale(stemWidth * 0.72)),
				stemTop.add(side.scale(stemWidth * 0.72)), palette.dark(), alpha * 0.82F);
		quad(buffer, pose, base.add(forward.scale(stemWidth)), base.subtract(forward.scale(stemWidth)),
				stemTop.subtract(forward.scale(stemWidth * 0.72)),
				stemTop.add(forward.scale(stemWidth * 0.72)), palette.main(), alpha);
		double capRadius = 0.22 + height * 0.08;
		ring(buffer, pose, stemTop.add(0.0, 0.01, 0.0), capRadius,
				Math.max(0.08, capRadius * 0.42), palette, alpha);
		ring(buffer, pose, stemTop.add(0.0, 0.025, 0.0), capRadius * 0.56,
				Math.max(0.055, capRadius * 0.24), palette, alpha * 0.9F);
	}

	private static void billboard(BufferBuilder buffer, Matrix4f pose, Vec3 from, Vec3 to,
			Vec3 side, double width, int color, float alpha) {
		Vec3 offset = side.scale(width);
		quad(buffer, pose, from.add(offset), from.subtract(offset), to.subtract(offset),
				to.add(offset), color, alpha);
	}

	private static void triangle(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c,
			int color, float alpha) {
		vertex(buffer, pose, a, color, alpha);
		vertex(buffer, pose, b, color, alpha);
		vertex(buffer, pose, c, color, alpha);
	}

	private static void quad(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
			int color, float alpha) {
		triangle(buffer, pose, a, b, c, color, alpha);
		triangle(buffer, pose, a, c, d, color, alpha);
	}

	private static void vertex(BufferBuilder buffer, Matrix4f pose, Vec3 point,
			int color, float alpha) {
		buffer.addVertex(pose, (float)point.x, (float)point.y, (float)point.z)
				.setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF,
						Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F));
	}

	private record Palette(int core, int main, int dark) {
	}
}
