package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.boss.MushroomAttackVolume;
import dev.tatami.virtualcraft.boss.MushroomComboPattern;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Particle-independent, depth-tested attack ranges for all Mushroom King attacks. */
public final class MushroomAttackTelegraphRenderer {
	private static final ResourceLocation TELEGRAPH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/effect/mushroom_telegraph.png");
	private static final int DARK = 0x081018;
	private static final int MID = 0x6F9995;
	private static final int HIGH = 0x38BFFF;
	private static final int LOW = 0xFFC247;
	private static final int MID_CORE = 0xD5EEEA;
	private static final int HIGH_CORE = 0xDDF8FF;
	private static final int LOW_CORE = 0xFFF0A8;
	private static final int MID_ACCENT = 0xB9788A;
	private static final int CANOPY_ACCENT = 0xC678B6;
	private static final int SPORE_ACCENT = 0xF09A75;
	private static final int ROOT_ACCENT = 0x65C7A7;
	private static final int CONVERGENCE_ACCENT = 0xA66B86;

	private MushroomAttackTelegraphRenderer() {
	}

	public static void render(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, TELEGRAPH_TEXTURE);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
				DefaultVertexFormat.POSITION_TEX_COLOR);
		Matrix4f pose = poseStack.last().pose();
		boolean wrote = false;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof VirtualMushroomKing boss && boss.isAlive()
					&& boss.getAttackState() != 0) {
				wrote |= renderAttack(buffer, pose, boss);
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
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		poseStack.popPose();
	}

	private static boolean renderAttack(BufferBuilder buffer, Matrix4f pose, VirtualMushroomKing boss) {
		int attack = boss.getAttackState();
		int tick = boss.getAttackTick();
		MushroomComboPattern combo = boss.getComboPattern();
		if (combo != null) {
			return renderCombo(buffer, pose, boss, combo, tick);
		}
		if (tick > MushroomKingAttackRules.activeEndTick(attack) + 2) {
			return false;
		}
		Vec3 origin = boss.position();
		Vec3 forward = forward(boss.getLockedYaw());
		float charge = charge(tick, MushroomKingAttackRules.warningTicks(attack));
		return switch (attack) {
			case MushroomKingAttackRules.ROOT_CADENCE -> {
				fan(buffer, pose, origin.add(0.0, 0.05, 0.0), forward,
					MushroomAttackVolume.radius(attack, 3),
					MushroomAttackVolume.angleDegrees(attack, 3),
					DARK, MID, MID_CORE, MID_ACCENT, 0.12F + charge * 0.18F);
				yield true;
			}
			case MushroomKingAttackRules.STEM_HAMMER, MushroomKingAttackRules.CAP_CLAP -> {
				fan(buffer, pose, origin.add(0.0, 0.055, 0.0), forward,
						MushroomAttackVolume.radius(attack, 1),
						MushroomAttackVolume.angleDegrees(attack, 1),
						DARK, MID, MID_CORE, MID_ACCENT, 0.13F + charge * 0.2F);
				yield true;
			}
			case MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY -> {
				Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
				for (int wave = 1; wave <= 3; wave++) {
					Vec3 laneOrigin = origin.add(side.scale(wave == 2 ? 0.9 : -0.9))
							.add(0.0, 1.35, 0.0);
					verticalLane(buffer, pose, laneOrigin, forward,
							MushroomAttackVolume.NEEDLE_LENGTH,
							MushroomAttackVolume.NEEDLE_HALF_WIDTH,
							1.05, 2.05, DARK, MID, MID_CORE, MID_ACCENT,
							0.14F + charge * 0.22F);
				}
				yield true;
			}
			case MushroomKingAttackRules.CANOPY_CLOSURE -> {
				verticalArc(buffer, pose, origin, forward,
						MushroomAttackVolume.radius(attack, 1),
						MushroomAttackVolume.angleDegrees(attack, 1),
						MushroomAttackVolume.HIGH_MIN_Y, MushroomAttackVolume.HIGH_MAX_Y,
						DARK, HIGH, HIGH_CORE, CANOPY_ACCENT, 0.30F + charge * 0.38F);
				yield true;
			}
			case MushroomKingAttackRules.SPORE_BREATH -> {
				int wave = nextWave(attack, tick);
				double pulseLength = 5.8 + wave * 1.15;
				sporeCorridor(buffer, pose, origin.add(0.0, 2.15, 0.0), forward,
						pulseLength, MushroomAttackVolume.BREATH_HALF_WIDTH,
						charge, 0.28F + charge * 0.46F);
				yield true;
			}
			case MushroomKingAttackRules.MYCELIAL_SURGE -> {
				int wave = nextWave(attack, tick);
				Vec3 waveOrigin = origin.add(forward.scale(MushroomAttackVolume.surgeStart(wave)))
						.add(0.0, 0.055, 0.0);
				rootGrowthTelegraph(buffer, pose, waveOrigin, forward,
						MushroomAttackVolume.surgeLength(wave),
						MushroomAttackVolume.SURGE_HALF_WIDTH, charge,
						0.24F + charge * 0.38F);
				yield true;
			}
			case MushroomKingAttackRules.ROOT_CONVERGENCE -> {
				ultimateFungalCircuit(buffer, pose, origin.add(0.0, 0.095, 0.0),
						boss.getAttackVariant(), charge, 0.66F + charge * 0.28F);
				yield true;
			}
			default -> false;
		};
	}

	private static void ultimateFungalCircuit(BufferBuilder buffer, Matrix4f pose,
			Vec3 center, int variant, float charge, float alpha) {
		double rotation = variant * Math.PI / 8.0 + charge * 0.10;
		double[] radii = {5.2, MushroomAttackVolume.CONVERGENCE_ARENA_RADIUS};
		double[] widths = {0.52, 0.78};
		for (int ring = 0; ring < radii.length; ring++) {
			float reveal = Mth.clamp(charge * 1.45F - ring * 0.16F, 0.24F, 1.0F);
			ringWithGap(buffer, pose, center.add(0.0, ring * 0.004, 0.0),
					radii[ring], widths[ring], rotation, -1.0,
					DARK, CONVERGENCE_ACCENT,
					ring == 1 ? LOW_CORE : MID_CORE,
					ROOT_ACCENT, alpha * reveal);
		}
		for (int spoke = 0; spoke < 8; spoke++) {
			double angle = rotation + Mth.TWO_PI * spoke / 8.0;
			Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			Vec3 inner = center.add(direction.scale(2.5));
			Vec3 outer = center.add(direction.scale(
					MushroomAttackVolume.CONVERGENCE_ARENA_RADIUS - 1.2));
			segmentedGroundLine(buffer, pose, inner, outer, 5, 0.075,
					DARK, (spoke & 1) == 0 ? ROOT_ACCENT : MID_CORE, alpha * 0.76F);
		}
		sporeRosetteGround(buffer, pose, center, 2.35, DARK,
				charge > 0.72F ? LOW_CORE : MID_CORE, alpha * 0.88F);
	}

	private static boolean renderCombo(BufferBuilder buffer, Matrix4f pose,
			VirtualMushroomKing boss, MushroomComboPattern combo, int tick) {
		if (tick > combo.endTick()) {
			return false;
		}
		int stepIndex = combo.cueStepIndex(tick);
		MushroomComboPattern.Step step = combo.step(stepIndex);
		int cue = combo.cueTick(stepIndex);
		float charge = Mth.clamp((tick - cue)
				/ (float)Math.max(1, step.impactTick() - cue), 0.0F, 1.0F);
		Vec3 origin = boss.position();
		Vec3 forward = forward(boss.getLockedYaw());
		if (step.direction() == BossAttackDirection.MID) {
			renderMidMoveTelegraph(buffer, pose, origin.add(0.0, 0.09, 0.0),
					forward, step.move(), charge, 0.68F + charge * 0.22F);
			return true;
		}
		if (step.direction() == BossAttackDirection.HIGH) {
			if (step.move() == MushroomComboPattern.Move.CANOPY_CLOSURE) {
				verticalArc(buffer, pose, origin, forward, 4.15, 92.0,
						MushroomAttackVolume.HIGH_MIN_Y, MushroomAttackVolume.HIGH_MAX_Y,
						DARK, HIGH, HIGH_CORE, CANOPY_ACCENT, 0.64F + charge * 0.26F);
			} else {
				sporeCorridor(buffer, pose, origin.add(0.0, 2.15, 0.0), forward,
						5.5, 2.35, charge, 0.62F + charge * 0.28F);
			}
			return true;
		}
		renderLowMoveTelegraph(buffer, pose, origin.add(0.0, 0.09, 0.0), forward,
				step.move(), combo, stepIndex, charge, 0.68F + charge * 0.22F);
		return true;
	}

	private static void renderLowMoveTelegraph(BufferBuilder buffer, Matrix4f pose,
			Vec3 origin, Vec3 forward, MushroomComboPattern.Move move,
			MushroomComboPattern combo, int stepIndex, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		switch (move) {
			case ROOT_STOMP_LEFT, ROOT_STOMP_RIGHT -> {
				double handedness = move == MushroomComboPattern.Move.ROOT_STOMP_LEFT ? 1.0 : -1.0;
				Vec3 foot = origin.add(forward.scale(0.8)).add(side.scale(handedness * 0.98));
				sporeRosetteGround(buffer, pose, foot, 2.35, DARK,
						charge > 0.68F ? LOW_CORE : ROOT_ACCENT, alpha);
				arcLine(buffer, pose, foot, 1.48, Math.atan2(forward.z, forward.x),
						Math.toRadians(118.0), 0.16, DARK, LOW_CORE, alpha);
			}
			case ROOT_SURGE -> {
				Vec3 waveOrigin = origin.add(forward.scale(combo.advanceStart(stepIndex)));
				rootGrowthTelegraph(buffer, pose, waveOrigin, forward,
						combo.advanceLength(stepIndex), MushroomAttackVolume.SURGE_HALF_WIDTH,
						charge, alpha);
			}
			case ROOT_CLAMP -> rootClampTelegraph(buffer, pose, origin, forward, charge, alpha);
			case CAP_QUAKE -> capQuakeTelegraph(buffer, pose, origin, forward, charge, alpha);
			case MYCELIUM_ERUPTION -> myceliumEruptionTelegraph(
					buffer, pose, origin, forward, charge, alpha);
			default -> {
				// High and mid moves are handled before this dispatcher.
			}
		}
	}

	private static void rootClampTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 contact = origin.add(forward.scale(2.45));
		for (double hand : new double[] {-1.0, 1.0}) {
			Vec3 root = origin.add(forward.scale(0.2)).add(side.scale(hand * 2.5));
			Vec3 elbow = origin.add(forward.scale(1.4)).add(side.scale(hand * 1.75));
			Vec3 fang = contact.add(forward.scale(0.45)).add(side.scale(hand * 0.28));
			groundLine(buffer, pose, root, elbow, 0.15, DARK, ROOT_ACCENT, alpha);
			groundLine(buffer, pose, elbow, fang, 0.19, DARK, LOW_CORE, alpha);
			groundLine(buffer, pose, fang, contact, 0.12, DARK, SPORE_ACCENT, alpha);
		}
		groundFungalSeal(buffer, pose, contact, forward, 0.92 + charge * 0.18, alpha);
	}

	private static void capQuakeTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, float charge, float alpha) {
		Vec3 center = origin.add(forward.scale(1.7));
		for (int ring = 1; ring <= 3; ring++) {
			double radius = 0.72 + ring * 0.72 + charge * ring * 0.08;
			sporeRosetteGround(buffer, pose, center.add(0.0, ring * 0.003, 0.0), radius,
					DARK, ring == 2 ? SPORE_ACCENT : LOW_CORE, alpha * (0.72F + ring * 0.08F));
		}
		groundFungalSeal(buffer, pose, center, forward, 0.86 + charge * 0.16, alpha);
	}

	private static void myceliumEruptionTelegraph(BufferBuilder buffer, Matrix4f pose,
			Vec3 origin, Vec3 forward, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 previous = origin.add(forward.scale(0.45));
		for (int burst = 1; burst <= 3; burst++) {
			Vec3 center = origin.add(forward.scale(1.2 + burst * 1.05))
					.add(side.scale((burst & 1) == 0 ? 0.48 : -0.48));
			groundLine(buffer, pose, previous, center, 0.14, DARK,
					burst == 2 ? LOW_CORE : ROOT_ACCENT, alpha);
			sporeRosetteGround(buffer, pose, center, 0.62 + burst * 0.22 + charge * 0.1,
					DARK, burst == 3 ? SPORE_ACCENT : LOW_CORE, alpha);
			previous = center;
		}
	}

	private static void renderMidMoveTelegraph(BufferBuilder buffer, Matrix4f pose,
			Vec3 origin, Vec3 forward, MushroomComboPattern.Move move,
			float charge, float alpha) {
		switch (move) {
			case ROOT_SWEEP_LEFT -> rootSweepTelegraph(buffer, pose, origin, forward,
					1.0, charge, alpha);
			case ROOT_SWEEP_RIGHT -> rootSweepTelegraph(buffer, pose, origin, forward,
					-1.0, charge, alpha);
			case CAP_CLAP -> capClapTelegraph(buffer, pose, origin, forward, charge, alpha);
			case STEM_HAMMER -> stemHammerTelegraph(buffer, pose, origin, forward, charge, alpha);
			case CAP_DRIVE -> capDriveTelegraph(buffer, pose, origin, forward, charge, alpha);
			case ROOT_UPPERCUT -> rootUppercutTelegraph(buffer, pose, origin, forward, charge, alpha);
			case SHOULDER_RUSH -> shoulderRushTelegraph(buffer, pose, origin, forward, charge, alpha);
			default -> {
				// Non-mid moves are rendered by their direction-specific branch.
			}
		}
	}

	private static void rootSweepTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, double handedness, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 shoulder = origin.add(forward.scale(0.45)).add(side.scale(handedness * 1.55));
		Vec3 middle = origin.add(forward.scale(2.05)).add(side.scale(handedness * 0.35));
		Vec3 contact = origin.add(forward.scale(3.55)).add(side.scale(-handedness * 0.82));
		groundLine(buffer, pose, shoulder, middle, 0.12, DARK, MID_ACCENT, alpha);
		groundLine(buffer, pose, middle, contact, 0.15, DARK, MID_CORE, alpha);
		Vec3 hook = contact.add(forward.scale(-0.38)).add(side.scale(handedness * 0.72));
		groundLine(buffer, pose, hook, contact, 0.10, DARK, SPORE_ACCENT, alpha * 0.9F);
		groundFungalSeal(buffer, pose, contact, forward, 0.68 + charge * 0.12, alpha);
	}

	private static void capClapTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 contact = origin.add(forward.scale(2.75));
		for (double hand : new double[] {-1.0, 1.0}) {
			Vec3 outer = origin.add(forward.scale(0.55)).add(side.scale(hand * 2.05));
			Vec3 fold = origin.add(forward.scale(1.65)).add(side.scale(hand * 1.0));
			groundLine(buffer, pose, outer, fold, 0.14, DARK,
					hand < 0.0 ? ROOT_ACCENT : MID_ACCENT, alpha);
			groundLine(buffer, pose, fold, contact, 0.17, DARK, MID_CORE, alpha);
			for (double rib = 0.28; rib <= 0.72; rib += 0.22) {
				Vec3 ribRoot = outer.lerp(fold, rib);
				Vec3 ribTip = ribRoot.add(forward.scale(0.42))
						.add(side.scale(-hand * 0.28));
				groundLine(buffer, pose, ribRoot, ribTip, 0.055, DARK,
						SPORE_ACCENT, alpha * 0.78F);
			}
		}
		groundFungalSeal(buffer, pose, contact, forward, 0.82 + charge * 0.12, alpha);
	}

	private static void stemHammerTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 contact = origin.add(forward.scale(3.45));
		groundLine(buffer, pose, origin.add(forward.scale(0.45)), contact,
				0.19, DARK, MID_CORE, alpha);
		for (double progress : new double[] {0.34, 0.58, 0.8}) {
			Vec3 center = origin.lerp(contact, progress);
			double width = 0.42 + progress * 0.5;
			groundLine(buffer, pose, center.add(side.scale(-width)),
					center.add(side.scale(width)), 0.08, DARK,
					progress > 0.7 ? SPORE_ACCENT : MID_ACCENT, alpha * 0.86F);
		}
		sporeRosetteGround(buffer, pose, contact, 1.18 + charge * 0.18,
				DARK, MID_CORE, alpha);
	}

	private static void capDriveTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 contact = origin.add(forward.scale(3.6));
		for (double lane : new double[] {-1.0, 0.0, 1.0}) {
			Vec3 start = origin.add(forward.scale(0.35)).add(side.scale(lane * 0.8));
			Vec3 end = contact.add(side.scale(lane * (1.55 - Math.abs(lane) * 0.18)));
			groundLine(buffer, pose, start, end, lane == 0.0 ? 0.11 : 0.14,
					DARK, lane == 0.0 ? SPORE_ACCENT : MID_CORE, alpha);
		}
		for (double offset : new double[] {-1.0, 1.0}) {
			Vec3 wing = contact.add(side.scale(offset * 1.38));
			groundLine(buffer, pose, wing, contact.add(forward.scale(0.34)),
					0.11, DARK, ROOT_ACCENT, alpha * 0.88F);
		}
		groundFungalSeal(buffer, pose, contact, forward, 0.98 + charge * 0.14, alpha);
	}

	private static void rootUppercutTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 start = origin.add(forward.scale(0.25)).add(side.scale(-0.72));
		Vec3 middle = origin.add(forward.scale(1.75)).add(side.scale(0.3));
		Vec3 contact = origin.add(forward.scale(3.25)).add(side.scale(0.48));
		groundLine(buffer, pose, start, middle, 0.14, DARK, ROOT_ACCENT, alpha);
		groundLine(buffer, pose, middle, contact, 0.17, DARK, MID_CORE, alpha);
		for (int knot = 1; knot <= 3; knot++) {
			Vec3 center = start.lerp(contact, knot / 4.0);
			groundFungalSeal(buffer, pose, center, forward, 0.24 + knot * 0.06,
					alpha * (0.62F + knot * 0.08F));
		}
		groundFungalSeal(buffer, pose, contact, forward, 0.72 + charge * 0.14, alpha);
	}

	private static void shoulderRushTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 end = origin.add(forward.scale(4.05));
		for (double lane : new double[] {-0.88, 0.88}) {
			groundLine(buffer, pose, origin.add(forward.scale(0.3)).add(side.scale(lane)),
					end.add(side.scale(lane * 1.35)), 0.16, DARK,
					lane < 0.0 ? MID_ACCENT : ROOT_ACCENT, alpha);
		}
		for (int brace = 1; brace <= 4; brace++) {
			Vec3 center = origin.lerp(end, brace / 5.0);
			double half = 0.62 + brace * 0.13;
			groundLine(buffer, pose, center.add(side.scale(-half)),
					center.add(side.scale(half)), 0.075, DARK,
					(brace & 1) == 0 ? MID_CORE : SPORE_ACCENT, alpha * 0.82F);
		}
		groundFungalSeal(buffer, pose, end, forward, 0.9 + charge * 0.12, alpha);
	}

	private static int nextWave(int attack, int tick) {
		for (int wave = 1; wave <= MushroomKingAttackRules.waveCount(attack); wave++) {
			if (tick <= MushroomKingAttackRules.impactTick(attack, wave) + 2) {
				return wave;
			}
		}
		return MushroomKingAttackRules.waveCount(attack);
	}

	private static void fungalMeleeTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, double length, double halfWidth, MushroomComboPattern.Move move,
			float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 start = origin.add(forward.scale(0.9));
		double baseWidth = 0.56;
		Vec3 leftBase = start.add(side.scale(baseWidth));
		Vec3 rightBase = start.add(side.scale(-baseWidth));
		Vec3 leftTip = origin.add(forward.scale(length)).add(side.scale(halfWidth));
		Vec3 rightTip = origin.add(forward.scale(length)).add(side.scale(-halfWidth));
		groundLine(buffer, pose, leftBase, leftTip, 0.045, DARK, MID_CORE, alpha);
		groundLine(buffer, pose, rightBase, rightTip, 0.045, DARK, MID_CORE, alpha);
		Vec3 previous = rightTip;
		for (int segment = 1; segment <= 8; segment++) {
			double lateral = -1.0 + segment * 0.25;
			double bulge = 0.30 * (1.0 - lateral * lateral);
			Vec3 next = origin.add(forward.scale(length + bulge))
					.add(side.scale(lateral * halfWidth));
			groundLine(buffer, pose, previous, next, 0.042, DARK, MID_CORE, alpha);
			previous = next;
		}
		for (double lateral : new double[] {-0.62, -0.28, 0.28, 0.62}) {
			Vec3 ribStart = start.add(side.scale(lateral * baseWidth * 0.55));
			Vec3 ribEnd = origin.add(forward.scale(length - 0.12 + 0.22 * (1.0 - lateral * lateral)))
					.add(side.scale(lateral * halfWidth * 0.92));
			groundLine(buffer, pose, ribStart, ribEnd, 0.027, DARK,
					Math.abs(lateral) < 0.4 ? MID_ACCENT : MID_CORE, alpha * 0.72F);
		}
		// Curved cross-gills turn the melee lane into a carved cap motif instead
		// of a plain cone. Each band has a darker root and a pale contact edge.
		for (double progress : new double[] {0.34, 0.56, 0.76}) {
			double width = Mth.lerp(progress, baseWidth, halfWidth) * 0.86;
			Vec3 center = origin.add(forward.scale(length * progress + 0.18));
			Vec3 left = center.add(side.scale(width));
			Vec3 right = center.add(side.scale(-width));
			Vec3 crest = center.add(forward.scale(0.18 + progress * 0.16));
			groundLine(buffer, pose, left, crest, 0.032, DARK,
					progress > 0.7 ? MID_CORE : MID_ACCENT, alpha * 0.78F);
			groundLine(buffer, pose, crest, right, 0.032, DARK,
					progress > 0.7 ? SPORE_ACCENT : MID_CORE, alpha * 0.78F);
		}
		groundFungalSeal(buffer, pose,
				origin.add(forward.scale(length * 0.78)), forward,
				Math.min(0.92, halfWidth * 0.46), alpha * 0.82F);
		double sweepSide = move == MushroomComboPattern.Move.ROOT_SWEEP_RIGHT ? -1.0 : 1.0;
		Vec3 sweepStart = start.add(side.scale(sweepSide * baseWidth * 0.82));
		Vec3 sweepMiddle = origin.add(forward.scale(length * 0.58))
				.add(side.scale(-sweepSide * halfWidth * 0.08));
		Vec3 sweepEnd = origin.add(forward.scale(length + 0.12))
				.add(side.scale(-sweepSide * halfWidth * 0.58));
		groundLine(buffer, pose, sweepStart, sweepMiddle, 0.038, DARK,
				charge > 0.74F ? MID_CORE : MID_ACCENT, alpha * 0.9F);
		groundLine(buffer, pose, sweepMiddle, sweepEnd, 0.038, DARK,
				charge > 0.74F ? MID_CORE : MID_ACCENT, alpha * 0.86F);
		if (move == MushroomComboPattern.Move.CAP_CLAP) {
			Vec3 contact = origin.add(forward.scale(length * 0.82));
			groundLine(buffer, pose, start.add(side.scale(halfWidth * 0.88)), contact,
					0.055, DARK, MID_CORE, alpha);
			groundLine(buffer, pose, start.add(side.scale(-halfWidth * 0.88)), contact,
					0.055, DARK, MID_CORE, alpha);
		} else if (move == MushroomComboPattern.Move.STEM_HAMMER
				|| move == MushroomComboPattern.Move.ROOT_UPPERCUT
				|| move == MushroomComboPattern.Move.SHOULDER_RUSH
				|| move == MushroomComboPattern.Move.CAP_DRIVE) {
			Vec3 contact = origin.add(forward.scale(length * 0.82));
			groundLine(buffer, pose, start, contact, 0.065, DARK, MID_CORE, alpha);
			sporeRosetteGround(buffer, pose, contact, move == MushroomComboPattern.Move.STEM_HAMMER
					? 0.72 : 0.92, DARK, MID_CORE, alpha);
		}
	}

	private static void rootGrowthTelegraph(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, double length, double halfWidth, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 end = origin.add(forward.scale(length));
		segmentedGroundLine(buffer, pose, origin.add(side.scale(halfWidth)),
				end.add(side.scale(halfWidth)), 4, 0.055, DARK, LOW_CORE, alpha * 0.88F);
		segmentedGroundLine(buffer, pose, origin.add(side.scale(-halfWidth)),
				end.add(side.scale(-halfWidth)), 4, 0.055, DARK, LOW_CORE, alpha * 0.88F);
		Vec3 previous = origin;
		for (int segment = 1; segment <= 7; segment++) {
			double progress = segment / 7.0;
			if (progress > 0.1 + charge * 0.95) {
				break;
			}
			double weave = ((segment & 1) == 0 ? 1.0 : -1.0)
					* halfWidth * (0.08 + segment * 0.008);
			Vec3 next = origin.lerp(end, progress).add(side.scale(weave));
			groundLine(buffer, pose, previous, next, 0.045, DARK,
					(segment & 1) == 0 ? LOW : ROOT_ACCENT, alpha * 0.9F);
			if (segment == 2 || segment == 4 || segment == 6) {
				double reach = halfWidth * (0.34 + segment * 0.035);
				Vec3 branchBack = forward.scale(-0.18 - segment * 0.012);
				groundLine(buffer, pose, next,
						next.add(side.scale(reach)).add(branchBack), 0.032, DARK,
						segment == 4 ? LOW_CORE : ROOT_ACCENT, alpha * 0.78F);
				groundLine(buffer, pose, next,
						next.add(side.scale(-reach * 0.82)).add(branchBack), 0.032, DARK,
						segment == 4 ? ROOT_ACCENT : LOW_CORE, alpha * 0.74F);
			}
			previous = next;
		}
		Vec3 crown = end.add(forward.scale(-0.28));
		groundLine(buffer, pose, crown, end.add(side.scale(halfWidth * 0.46)),
				0.042, DARK, LOW_CORE, alpha * 0.9F);
		groundLine(buffer, pose, crown, end.add(side.scale(-halfWidth * 0.46)),
				0.042, DARK, ROOT_ACCENT, alpha * 0.9F);
		groundFungalSeal(buffer, pose, crown, forward,
				Math.min(0.82, halfWidth * 0.5), alpha * 0.8F);
	}

	private static void segmentedGroundLine(BufferBuilder buffer, Matrix4f pose, Vec3 start,
			Vec3 end, int segments, double width, int dark, int core, float alpha) {
		for (int segment = 0; segment < segments; segment++) {
			double from = segment / (double)segments;
			double to = Math.min(1.0, from + 0.68 / segments);
			groundLine(buffer, pose, start.lerp(end, from), start.lerp(end, to),
					width, dark, core, alpha);
		}
	}

	private static void sporeCorridor(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, double length, double halfWidth, float charge, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 end = origin.add(forward.scale(length));
		Vec3 leftStart = origin.add(side.scale(halfWidth));
		Vec3 rightStart = origin.add(side.scale(-halfWidth));
		Vec3 leftEnd = end.add(side.scale(halfWidth * 0.72)).add(0.0, 0.28, 0.0);
		Vec3 rightEnd = end.add(side.scale(-halfWidth * 0.72)).add(0.0, 0.28, 0.0);
		quad(buffer, pose, leftStart, rightStart, rightEnd, leftEnd,
				0xFFFFFF, alpha * 0.26F);
		planeLine(buffer, pose, leftStart, leftEnd, side, 0.13, DARK, HIGH_CORE, alpha);
		planeLine(buffer, pose, rightStart, rightEnd, side, 0.13, DARK, SPORE_ACCENT, alpha);
		for (int node = 1; node <= 4; node++) {
			double progress = node / 4.5;
			Vec3 center = origin.lerp(end, progress).add(0.0,
					0.08 + Math.sin(progress * Math.PI) * 0.32, 0.0);
			double size = 0.28 + node * 0.055 + charge * 0.12;
			airbornePodMarker(buffer, pose, center, forward, size,
					DARK, HIGH, HIGH_CORE, node % 2 == 0 ? SPORE_ACCENT : CANOPY_ACCENT,
					alpha * (0.72F + node * 0.05F));
		}
	}

	private static void sporeRosetteGround(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			double radius, int dark, int core, float alpha) {
		Vec3 previousOuter = center.add(radius, 0.0, 0.0);
		for (int point = 1; point <= 12; point++) {
			double angle = Mth.TWO_PI * point / 12.0;
			Vec3 outer = center.add(Math.cos(angle) * radius, 0.0,
					Math.sin(angle) * radius);
			groundLine(buffer, pose, previousOuter, outer, 0.065, dark, core, alpha);
			if (point % 3 == 0) {
				Vec3 inner = center.add(Math.cos(angle) * radius * 0.48, 0.0,
						Math.sin(angle) * radius * 0.48);
				groundLine(buffer, pose, inner, outer, 0.045, dark, ROOT_ACCENT,
						alpha * 0.76F);
			}
			previousOuter = outer;
		}
	}

	private static void groundFungalSeal(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			Vec3 forward, double halfSize, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x).normalize();
		Vec3 depth = forward.normalize();
		Vec3 a = center.add(side.scale(-halfSize)).add(depth.scale(-halfSize));
		Vec3 b = center.add(side.scale(halfSize)).add(depth.scale(-halfSize));
		Vec3 c = center.add(side.scale(halfSize)).add(depth.scale(halfSize));
		Vec3 d = center.add(side.scale(-halfSize)).add(depth.scale(halfSize));
		quad(buffer, pose, a, b, c, d, 0xFFFFFF, alpha);
	}

	private static void verticalLane(BufferBuilder buffer, Matrix4f pose, Vec3 origin, Vec3 forward,
			double length, double halfWidth, double bottom, double top,
			int dark, int main, int core, int accent, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 end = origin.add(forward.scale(length));
		Vec3 a = origin.add(side.scale(halfWidth)).add(0.0, bottom, 0.0);
		Vec3 b = origin.add(side.scale(-halfWidth)).add(0.0, bottom, 0.0);
		Vec3 c = end.add(side.scale(-halfWidth)).add(0.0, top, 0.0);
		Vec3 d = end.add(side.scale(halfWidth)).add(0.0, top, 0.0);
		groundLine(buffer, pose, a, d, 0.14, dark, core, alpha);
		groundLine(buffer, pose, b, c, 0.14, dark, accent, alpha);
		groundLine(buffer, pose, a.lerp(b, 0.5), d.lerp(c, 0.5),
				0.055, dark, main, alpha * 0.72F);
		for (int rung = 1; rung <= 4; rung++) {
			double progress = rung / 5.0;
			groundLine(buffer, pose, a.lerp(d, progress), b.lerp(c, progress),
					0.045, dark, (rung & 1) == 0 ? core : accent, alpha * 0.66F);
		}
	}

	private static void airbornePodMarker(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			Vec3 forward, double radius, int dark, int main, int core, int accent, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x).normalize();
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		double outer = radius;
		double inner = radius * 0.62;
		outlineDiamond(buffer, pose, center, side, up, forward, outer,
				dark, accent, alpha);
		outlineDiamond(buffer, pose, center, side, up, forward, inner,
				dark, core, alpha * 0.9F);
		planeLine(buffer, pose, center.add(side.scale(-inner)), center.add(side.scale(inner)),
				forward, 0.055, dark, main, alpha * 0.78F);
		planeLine(buffer, pose, center.add(up.scale(-inner)), center.add(up.scale(inner)),
				forward, 0.055, dark, SPORE_ACCENT, alpha * 0.78F);
	}

	private static void ringWithGap(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			double radius, double halfWidth, double gapAngle, double gapHalf,
			int dark, int main, int core, int accent, float alpha) {
		int segments = 40;
		for (int i = 0; i < segments; i++) {
			double a0 = Mth.TWO_PI * i / segments;
			double a1 = Mth.TWO_PI * (i + 1) / segments;
			double midpoint = (a0 + a1) * 0.5;
			double delta = Math.atan2(Math.sin(midpoint - gapAngle), Math.cos(midpoint - gapAngle));
			if (Math.abs(delta) <= gapHalf) {
				continue;
			}
			Vec3 i0 = center.add(Math.cos(a0) * (radius - halfWidth), 0.0,
					Math.sin(a0) * (radius - halfWidth));
			Vec3 i1 = center.add(Math.cos(a1) * (radius - halfWidth), 0.0,
					Math.sin(a1) * (radius - halfWidth));
			Vec3 o1 = center.add(Math.cos(a1) * (radius + halfWidth), 0.0,
					Math.sin(a1) * (radius + halfWidth));
			Vec3 o0 = center.add(Math.cos(a0) * (radius + halfWidth), 0.0,
					Math.sin(a0) * (radius + halfWidth));
			quad(buffer, pose, i0, i1, o1, o0, 0xFFFFFF, alpha * 0.26F);
			groundLine(buffer, pose, o0, o1, 0.10, dark,
					i % 5 == 0 ? accent : core, alpha);
			if (i % 5 == 0) {
				groundLine(buffer, pose, i0, o0, 0.055, dark, main, alpha * 0.78F);
			}
		}
	}

	private static float charge(int tick, int warningTick) {
		return Mth.clamp(tick / (float)Math.max(1, warningTick), 0.0F, 1.0F);
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

	private static void fan(BufferBuilder buffer, Matrix4f pose, Vec3 origin, Vec3 forward,
			double radius, double degrees, int dark, int main, int core, int accent, float alpha) {
		double center = Math.atan2(forward.z, forward.x);
		double half = Math.toRadians(degrees * 0.5);
		int segments = 28;
		for (int i = 0; i < segments; i++) {
			double a0 = center - half + degreesToRadians(degrees) * i / segments;
			double a1 = center - half + degreesToRadians(degrees) * (i + 1) / segments;
			Vec3 p0 = origin.add(Math.cos(a0) * radius, 0.0, Math.sin(a0) * radius);
			Vec3 p1 = origin.add(Math.cos(a1) * radius, 0.0, Math.sin(a1) * radius);
			if (i == 0 || i == segments - 1) {
				groundLine(buffer, pose, origin, i == 0 ? p0 : p1, 0.14, dark, core, alpha);
			}
			if (i % 5 == 0) {
				groundLine(buffer, pose, origin.lerp(p0, 0.38), p0,
						0.045, dark, (i & 1) == 0 ? accent : main, alpha * 0.72F);
			}
		}
		ringArc(buffer, pose, origin, radius - 0.28, radius, center, half, dark, main, core, alpha);
	}

	private static double degreesToRadians(double degrees) {
		return Math.toRadians(degrees);
	}

	private static void ribbon(BufferBuilder buffer, Matrix4f pose, Vec3 origin, Vec3 forward,
			double length, double halfWidth, int dark, int main, int core, int accent, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 end = origin.add(forward.scale(length));
		groundLine(buffer, pose, origin.add(side.scale(halfWidth)), end.add(side.scale(halfWidth)),
				0.18, dark, core, alpha);
		groundLine(buffer, pose, origin.add(side.scale(-halfWidth)), end.add(side.scale(-halfWidth)),
				0.18, dark, accent, alpha);
		groundLine(buffer, pose, origin, end, 0.055, dark, main, alpha * 0.68F);
		for (int rung = 1; rung <= 6; rung++) {
			Vec3 center = origin.lerp(end, rung / 7.0);
			groundLine(buffer, pose, center.add(side.scale(-halfWidth * 0.82)),
					center.add(side.scale(halfWidth * 0.82)), 0.04, dark,
					(rung & 1) == 0 ? core : accent, alpha * 0.7F);
		}
	}

	private static void rootBranchPattern(BufferBuilder buffer, Matrix4f pose, Vec3 origin,
			Vec3 forward, double length, double halfWidth, int dark, int core, int accent, float alpha) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		for (int branch = 1; branch <= 5; branch++) {
			double progress = branch / 6.0;
			Vec3 center = origin.add(forward.scale(length * progress));
			double reach = halfWidth * (branch % 2 == 0 ? 0.86 : 0.62);
			Vec3 backward = forward.scale(-0.48);
			Vec3 left = center.add(side.scale(reach)).add(backward);
			Vec3 right = center.add(side.scale(-reach)).add(backward);
			groundLine(buffer, pose, center, left, 0.07, dark,
					(branch & 1) == 0 ? accent : core, alpha);
			groundLine(buffer, pose, center, right, 0.07, dark,
					(branch & 1) == 0 ? core : accent, alpha);
		}
	}

	private static void verticalArc(BufferBuilder buffer, Matrix4f pose, Vec3 origin, Vec3 forward,
			double radius, double degrees, double bottom, double top,
			int dark, int main, int core, int accent, float alpha) {
		double center = Math.atan2(forward.z, forward.x);
		double half = Math.toRadians(degrees * 0.5);
		int segments = 32;
		for (int i = 0; i < segments; i++) {
			double a0 = center - half + half * 2.0 * i / segments;
			double a1 = center - half + half * 2.0 * (i + 1) / segments;
			Vec3 p0 = origin.add(Math.cos(a0) * radius, bottom, Math.sin(a0) * radius);
			Vec3 p1 = origin.add(Math.cos(a1) * radius, bottom, Math.sin(a1) * radius);
			Vec3 p2 = origin.add(Math.cos(a1) * radius, top, Math.sin(a1) * radius);
			Vec3 p3 = origin.add(Math.cos(a0) * radius, top, Math.sin(a0) * radius);
			quad(buffer, pose, p0, p1, p2, p3, 0xFFFFFF, alpha * 0.20F);
			if (i % 4 == 0) {
				planeLine(buffer, pose, p0, p3, forward, 0.055, dark,
						(i & 4) == 0 ? accent : main, alpha * 0.78F);
			}
		}
		arcLine(buffer, pose, origin.add(0.0, bottom, 0.0), radius, center, half,
				0.15, dark, core, alpha);
		arcLine(buffer, pose, origin.add(0.0, top, 0.0), radius, center, half,
				0.11, dark, accent, alpha * 0.92F);
	}

	private static void ringArc(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			double inner, double outer, double angle, double half,
			int dark, int main, int core, float alpha) {
		int segments = 28;
		for (int i = 0; i < segments; i++) {
			double a0 = angle - half + half * 2.0 * i / segments;
			double a1 = angle - half + half * 2.0 * (i + 1) / segments;
			if (i % 4 == 0) {
				groundLine(buffer, pose,
						center.add(Math.cos(a0) * inner, 0.0, Math.sin(a0) * inner),
						center.add(Math.cos(a0) * outer, 0.0, Math.sin(a0) * outer),
						0.045, dark, main, alpha * 0.66F);
			}
		}
		arcLine(buffer, pose, center, outer, angle, half, 0.12, dark, core, alpha);
	}

	private static void arcLine(BufferBuilder buffer, Matrix4f pose, Vec3 center, double radius,
			double angle, double half, double width, int dark, int core, float alpha) {
		int segments = 48;
		Vec3 previous = center.add(Math.cos(angle - half) * radius, 0.0,
				Math.sin(angle - half) * radius);
		for (int i = 1; i <= segments; i++) {
			double a = angle - half + half * 2.0 * i / segments;
			Vec3 next = center.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
			groundLine(buffer, pose, previous, next, width, dark, core, alpha);
			previous = next;
		}
	}

	private static void groundLine(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b,
			double width, int dark, int core, float alpha) {
		Vec3 axis = b.subtract(a);
		if (axis.lengthSqr() < 1.0E-7) {
			return;
		}
		int segments = Mth.clamp((int)Math.ceil(axis.length() / 0.9), 1, 18);
		for (int index = 0; index < segments; index++) {
			Vec3 start = a.lerp(b, index / (double)segments);
			Vec3 end = a.lerp(b, Math.min(1.0, (index + 0.95) / segments));
			groundLineSegment(buffer, pose, start, end, Math.max(0.06, width * 1.22),
					dark, core, alpha);
		}
	}

	private static void groundLineSegment(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b,
			double width, int dark, int core, float alpha) {
		Vec3 axis = b.subtract(a);
		if (axis.lengthSqr() < 1.0E-7) {
			return;
		}
		Vec3 side = new Vec3(-axis.z, 0.0, axis.x).normalize();
		quadStrip(buffer, pose, a.add(side.scale(width * 2.15)), a.add(side.scale(-width * 2.15)),
				b.add(side.scale(-width * 2.05)), b.add(side.scale(width * 2.05)), dark, alpha * 0.9F);
		quadStrip(buffer, pose, a.add(side.scale(width)), a.add(side.scale(-width)),
				b.add(side.scale(-width)), b.add(side.scale(width)), core, alpha);
	}

	private static void outlineDiamond(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			Vec3 side, Vec3 up, Vec3 normal, double size,
			int dark, int core, float alpha) {
		Vec3 left = center.add(side.scale(-size));
		Vec3 top = center.add(up.scale(size));
		Vec3 right = center.add(side.scale(size));
		Vec3 bottom = center.add(up.scale(-size));
		planeLine(buffer, pose, left, top, normal, 0.09, dark, core, alpha);
		planeLine(buffer, pose, top, right, normal, 0.09, dark, core, alpha);
		planeLine(buffer, pose, right, bottom, normal, 0.09, dark, core, alpha);
		planeLine(buffer, pose, bottom, left, normal, 0.09, dark, core, alpha);
	}

	private static void planeLine(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b,
			Vec3 normal, double width, int dark, int core, float alpha) {
		Vec3 axis = b.subtract(a);
		if (axis.lengthSqr() < 1.0E-7) {
			return;
		}
		int segments = Mth.clamp((int)Math.ceil(axis.length() / 0.78), 1, 18);
		for (int index = 0; index < segments; index++) {
			Vec3 start = a.lerp(b, index / (double)segments);
			Vec3 end = a.lerp(b, (index + 0.82) / segments);
			planeLineSegment(buffer, pose, start, end, normal, width, dark, core, alpha);
		}
	}

	private static void planeLineSegment(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b,
			Vec3 normal, double width, int dark, int core, float alpha) {
		Vec3 perpendicular = b.subtract(a).cross(normal);
		if (perpendicular.lengthSqr() < 1.0E-7) {
			return;
		}
		Vec3 side = perpendicular.normalize();
		quadStrip(buffer, pose, a.add(side.scale(width * 1.9)), a.add(side.scale(-width * 1.9)),
				b.add(side.scale(-width * 1.8)), b.add(side.scale(width * 1.8)), dark, alpha * 0.78F);
		quadStrip(buffer, pose, a.add(side.scale(width)), a.add(side.scale(-width)),
				b.add(side.scale(-width)), b.add(side.scale(width)), core, alpha);
	}

	private static void quad(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
			int color, float alpha) {
		vertex(buffer, pose, a, color, alpha, 0.0F, 0.0F);
		vertex(buffer, pose, b, color, alpha, 0.5F, 0.0F);
		vertex(buffer, pose, c, color, alpha, 0.5F, 1.0F);
		vertex(buffer, pose, a, color, alpha, 0.0F, 0.0F);
		vertex(buffer, pose, c, color, alpha, 0.5F, 1.0F);
		vertex(buffer, pose, d, color, alpha, 0.0F, 1.0F);
	}

	private static void quadStrip(BufferBuilder buffer, Matrix4f pose,
			Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
		vertex(buffer, pose, a, color, alpha, 0.5F, 0.0F);
		vertex(buffer, pose, b, color, alpha, 1.0F, 0.0F);
		vertex(buffer, pose, c, color, alpha, 1.0F, 1.0F);
		vertex(buffer, pose, a, color, alpha, 0.5F, 0.0F);
		vertex(buffer, pose, c, color, alpha, 1.0F, 1.0F);
		vertex(buffer, pose, d, color, alpha, 0.5F, 1.0F);
	}

	private static void vertex(BufferBuilder buffer, Matrix4f pose, Vec3 point,
			int color, float alpha, float u, float v) {
		float visibleAlpha = Mth.clamp(alpha, 0.0F, 0.90F);
		buffer.addVertex(pose, (float)point.x, (float)point.y, (float)point.z)
				.setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF,
						Math.round(visibleAlpha * 255.0F))
				.setUv(u, v);
	}
}
