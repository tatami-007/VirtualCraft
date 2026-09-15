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
import dev.tatami.virtualcraft.boss.SnakeAttackVolume;
import dev.tatami.virtualcraft.boss.SnakeDashRules;
import dev.tatami.virtualcraft.boss.SnakeHuntPattern;
import dev.tatami.virtualcraft.boss.SnakeKingAttackRules;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class SnakeAttackTelegraphRenderer {
	private static final ResourceLocation TELEGRAPH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/effect/snake_telegraph.png");

	private SnakeAttackTelegraphRenderer() {
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
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, TELEGRAPH_TEXTURE);

		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
				DefaultVertexFormat.POSITION_TEX_COLOR);
		Matrix4f pose = poseStack.last().pose();
		boolean wrote = false;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof VirtualSnakeKing snake) || !snake.isAlive()
					|| snake.getAttackState() == VirtualSnakeKing.ATTACK_NONE) {
				continue;
			}
			wrote |= renderAttack(buffer, pose, snake);
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

	private static boolean renderAttack(BufferBuilder buffer, Matrix4f pose, VirtualSnakeKing snake) {
		int attack = snake.getAttackState();
		int tick = snake.getAttackTick();
		double yaw = Math.toRadians(snake.getLockedAttackYaw());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double x = snake.getX();
		double y = snake.getY() + 0.045;
		double z = snake.getZ();
		return switch (attack) {
			case VirtualSnakeKing.ATTACK_BITE -> renderBiteImpact(
					buffer, pose, x, y, z, fx, fz, tick);
			case VirtualSnakeKing.ATTACK_DOUBLE_HUNT -> renderHuntImpact(
					buffer, pose, snake, x, y, z, fx, fz, tick);
			case VirtualSnakeKing.ATTACK_TAIL_SWEEP -> renderTailSweep(
					buffer, pose, snake, x, y, z, tick);
			case VirtualSnakeKing.ATTACK_DATA_FANGS -> {
				if (tick >= SnakeKingAttackRules.HIGH_POISON_WARNING_START
						&& tick <= SnakeKingAttackRules.HIGH_POISON_HIT_TICK + 2) {
					double beamY = snake.getLockedTargetY();
					float charge = Math.min(1.0F,
							(tick - SnakeKingAttackRules.HIGH_POISON_WARNING_START)
									/ (float)(SnakeKingAttackRules.HIGH_POISON_HIT_TICK
											- SnakeKingAttackRules.HIGH_POISON_WARNING_START));
					float alpha = tick >= SnakeKingAttackRules.HIGH_POISON_HIT_TICK
							? 0.98F : 0.78F + charge * 0.18F;
					double rail = SnakeAttackVolume.HIGH_POISON_HALF_WIDTH - 0.18;
					ribbon(buffer, pose, x, beamY - 0.012, z, fx, fz,
							SnakeAttackVolume.HIGH_POISON_RANGE,
							SnakeAttackVolume.HIGH_POISON_HALF_WIDTH, 0.0,
							0x07121C, 0x164F73, 0.28F + charge * 0.08F);
					layeredRibbon(buffer, pose, x, beamY, z, fx, fz,
							SnakeAttackVolume.HIGH_POISON_RANGE, 0.24, rail,
							0x071723, 0x238BC3, 0xD9F8FF, alpha);
					layeredRibbon(buffer, pose, x, beamY, z, fx, fz,
							SnakeAttackVolume.HIGH_POISON_RANGE, 0.24, -rail,
							0x071723, 0x42C5ED, 0xFFFFFF, alpha);
					verticalRibbon(buffer, pose, x, beamY - 0.32, beamY + 0.32, z,
							fx, fz, SnakeAttackVolume.HIGH_POISON_RANGE, 0.18, 0.0,
							0x071723, tick >= SnakeKingAttackRules.HIGH_POISON_HIT_TICK
									? 0xFFFFFF : 0xBEEFFF, alpha * 0.82F);
					yield true;
				}
				yield false;
			}
			case VirtualSnakeKing.ATTACK_CROWN_CHAIN -> renderCrownChain(
					buffer, pose, x, y, z, tick);
			case VirtualSnakeKing.ATTACK_RUNE_SIGIL -> renderGroundPoison(
					buffer, pose, x, y, z, fx, fz, tick);
			case VirtualSnakeKing.ATTACK_VOID_DASH -> {
				if (isDashTelegraphVisible(tick)) {
					boolean active = SnakeDashRules.isActive(tick);
					float alpha = active ? 0.98F : 0.78F;
					ribbon(buffer, pose, x, y + 0.045, z, fx, fz, SnakeAttackVolume.DASH_RANGE,
							SnakeAttackVolume.DASH_HALF_WIDTH, 0.0,
							0x090D0F, 0x7A693D, active ? 0.42F : 0.3F);
					layeredRibbon(buffer, pose, x, y + 0.06, z, fx, fz,
							SnakeAttackVolume.DASH_RANGE, active ? 0.48 : 0.30, 0.0,
							0x11171A, 0xD8BE72, 0xFFFFFF, alpha);
					layeredRibbon(buffer, pose, x, y + 0.075, z, fx, fz,
							SnakeAttackVolume.DASH_RANGE, 0.16,
							SnakeAttackVolume.DASH_HALF_WIDTH - 0.12,
							0x10242B, 0x6AD9ED, 0xEFFFFF, alpha * 0.82F);
					layeredRibbon(buffer, pose, x, y + 0.075, z, fx, fz,
							SnakeAttackVolume.DASH_RANGE, 0.16,
							-SnakeAttackVolume.DASH_HALF_WIDTH + 0.12,
							0x281D10, 0xE5B83D, 0xFFF8D4, alpha * 0.82F);
					yield true;
				}
				yield false;
			}
			case VirtualSnakeKing.ATTACK_CROWN_JUDGMENT -> renderJudgment(
					buffer, pose, snake, x, y, z, tick);
			case VirtualSnakeKing.ATTACK_CORROSIVE_COIL -> renderCorrosiveCoil(
					buffer, pose, snake, x, y, z, tick);
			case VirtualSnakeKing.ATTACK_OVERHEAD_FANG -> renderOverheadImpact(
					buffer, pose, x, y, z, fx, fz, tick);
			default -> false;
		};
	}

	private static boolean renderBiteImpact(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double fx, double fz, int tick) {
		if (tick < 3 || tick > SnakeHuntPattern.BITE_HIT_TICK + 3) {
			return false;
		}
		double cx = x + fx * 3.0;
		double cz = z + fz * 3.0;
		float charge = tick < SnakeHuntPattern.BITE_HIT_TICK
				? 0.72F + tick * 0.018F
				: Math.max(0.28F, 0.98F - (tick - SnakeHuntPattern.BITE_HIT_TICK) * 0.22F);
		layeredFanOutline(buffer, pose, x, y + 0.04, z, fx, fz,
				SnakeAttackVolume.BITE_RANGE, SnakeAttackVolume.BITE_HALF_ANGLE,
				0x100D09, 0x9E8237, 0xFFF5D1, charge * 0.72F);
		layeredFangMaw(buffer, pose, cx, y + 1.1, cz, fx, fz,
				biteClosure(tick, SnakeHuntPattern.BITE_HIT_TICK), 1.42, 0.92,
				0x17130E, 0xE8C557, 0xFFFFFF, charge);
		return true;
	}

	private static boolean renderTailSweep(BufferBuilder buffer, Matrix4f pose, VirtualSnakeKing snake,
			double x, double y, double z, int tick) {
		if (tick < 6 || tick > SnakeHuntPattern.TAIL_HIT_TICK + 3) {
			return false;
		}
		double direction = SnakeHuntPattern.tailSweepDirection(snake.isSweepLeft());
		double sampleTick = Math.max(0.0, tick - 0.5);
		double center = Math.toRadians(snake.getLockedAttackYaw()) + Math.PI
				+ direction * (SnakeHuntPattern.tailBodySpinPhase(sampleTick)
						+ SnakeHuntPattern.tailPose(sampleTick).tip());
		float alpha = tick < SnakeHuntPattern.TAIL_HIT_TICK ? 0.72F + tick * 0.012F
				: Math.max(0.3F, 0.98F - (tick - SnakeHuntPattern.TAIL_HIT_TICK) * 0.16F);
		layeredArc(buffer, pose, x, y + 0.045, z, 0.0,
				SnakeAttackVolume.TAIL_INNER_RADIUS, SnakeAttackVolume.TAIL_OUTER_RADIUS,
				Math.PI, 0x0E0C09, 0x705B27, 0xF7E7B1, alpha * 0.44F);
		layeredVerticalArc(buffer, pose, x, z, center, 5.25, 0.82, y + 0.04, y + 1.7,
				0x12100C, 0xE2BB4B, 0xFFFFFF, alpha);
		layeredVerticalArc(buffer, pose, x, z, center - direction * 0.18, 4.8, 0.64,
				y + 0.08, y + 1.05, 0x0A1820, 0x68CDE3, 0xE8FCFF, alpha * 0.82F);
		layeredVerticalArc(buffer, pose, x, z, center - direction * 0.36, 4.35, 0.46,
				y + 0.1, y + 0.72, 0x15110B, 0xA96B25, 0xFFE6A1, alpha * 0.58F);
		return true;
	}

	private static boolean renderCorrosiveCoil(BufferBuilder buffer, Matrix4f pose,
			VirtualSnakeKing snake, double x, double y, double z, int tick) {
		if (tick < SnakeKingAttackRules.COIL_WARNING_START
				|| tick > SnakeKingAttackRules.COIL_RELEASE_END + 3) {
			return false;
		}
		float charge = Mth.clamp((tick - SnakeKingAttackRules.COIL_WARNING_START)
				/ (float)(SnakeKingAttackRules.COIL_RELEASE_START
						- SnakeKingAttackRules.COIL_WARNING_START), 0.0F, 1.0F);
		double radius = tick < SnakeKingAttackRules.COIL_RELEASE_START
				? Mth.lerp(charge, SnakeKingAttackRules.COIL_MAX_RADIUS,
						SnakeKingAttackRules.COIL_MIN_RADIUS)
				: SnakeKingAttackRules.coilRadius(tick);
		float pulse = 0.72F + 0.18F * Mth.sin(tick * 0.55F);
		layeredArc(buffer, pose, x, y + 0.055, z, 0.0,
				Math.max(0.08, radius - 0.42), radius + 0.42, Math.PI,
				0x130E06, 0xD98420, 0xFFF2B0,
				tick >= SnakeKingAttackRules.COIL_RELEASE_START ? 0.98F : pulse);
		if (tick < SnakeKingAttackRules.COIL_RELEASE_START) {
			layeredArc(buffer, pose, x, y + 0.035, z, 0.0,
					SnakeKingAttackRules.COIL_MAX_RADIUS - 0.14,
					SnakeKingAttackRules.COIL_MAX_RADIUS + 0.14, Math.PI,
					0x100D08, 0x6E5420, 0xFFE8A0, 0.62F + charge * 0.22F);
		}
		return true;
	}

	private static boolean renderCrownChain(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, int tick) {
		if (tick < SnakeKingAttackRules.CROWN_CHAIN_WARNING_START
				|| tick > SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK + 3) {
			return false;
		}
		float charge = Math.min(1.0F,
				(tick - SnakeKingAttackRules.CROWN_CHAIN_WARNING_START)
						/ (float)(SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK
								- SnakeKingAttackRules.CROWN_CHAIN_WARNING_START));
		float alpha = tick >= SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK
				? 0.96F : 0.38F + charge * 0.42F;
		for (int index = 0; index < 3; index++) {
			double radius = 5.8 + index * 6.0;
			layeredArc(buffer, pose, x, y + 0.04 + index * 0.008, z,
					tick * 0.025 * (index % 2 == 0 ? 1.0 : -1.0),
					radius - 0.16, radius + 0.16, Math.PI,
					0x07131A, 0x287DA4, 0xE8FCFF, alpha - index * 0.05F);
		}
		for (int index = 0; index < 8; index++) {
			double angle = Math.PI * 2.0 * index / 8.0 + tick * 0.008;
			lineRibbon(buffer, pose, x, y + 0.055, z,
					x + Math.cos(angle) * 18.0, z + Math.sin(angle) * 18.0,
					tick >= SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK ? 0.075 : 0.04,
					0x173C4D,
					tick >= SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK ? 0xF5FEFF : 0x72C9E6,
					alpha * 0.72F);
		}
		return true;
	}

	private static boolean renderHuntImpact(BufferBuilder buffer, Matrix4f pose, VirtualSnakeKing snake,
			double x, double y, double z, double fx, double fz, int tick) {
		SnakeHuntPattern pattern = snake.getHuntPattern();
		if (SnakeHuntPattern.isComboOpening(tick)) {
			return false;
		}
		int segment = pattern.comboSegmentAt(tick);
		if (segment >= pattern.segmentCount()) {
			return false;
		}
		int local = pattern.comboLocalTickAt(tick);
		if (pattern.move(segment) == SnakeHuntPattern.Move.BITE) {
			if (local < 3 || local > SnakeHuntPattern.BITE_HIT_TICK + 3) {
				return false;
			}
			float alpha = local <= SnakeHuntPattern.BITE_HIT_TICK
					? 0.72F + local * 0.025F
					: Math.max(0.28F, 0.96F - (local - SnakeHuntPattern.BITE_HIT_TICK) * 0.2F);
			layeredFanOutline(buffer, pose, x, y + 0.04, z, fx, fz,
					SnakeAttackVolume.BITE_RANGE, SnakeAttackVolume.BITE_HALF_ANGLE,
					0x100D09, 0x947A32, 0xFFF5D1, alpha * 0.7F);
			double cx = x + fx * 3.0;
			double cz = z + fz * 3.0;
			layeredFangMaw(buffer, pose, cx, y + 1.05, cz, fx, fz,
					biteClosure(local, SnakeHuntPattern.BITE_HIT_TICK), 1.32, 0.84,
					0x16120C, 0xE6C65F, 0xFFFFFF, alpha);
			return true;
		}
		if (local < 4 || local > SnakeHuntPattern.TAIL_HIT_TICK + 2) {
			return false;
		}
		double direction = SnakeHuntPattern.tailSweepDirection(snake.isSweepLeft());
		double sampleTick = Math.max(0.0, local - 0.5);
		double center = Math.toRadians(snake.getLockedAttackYaw()) + Math.PI
				+ direction * (SnakeHuntPattern.tailBodySpinPhase(sampleTick)
						+ SnakeHuntPattern.tailPose(sampleTick).tip());
		float alpha = local <= SnakeHuntPattern.TAIL_HIT_TICK
				? 0.72F + local * 0.015F
				: Math.max(0.3F, 0.96F - (local - SnakeHuntPattern.TAIL_HIT_TICK) * 0.22F);
		layeredArc(buffer, pose, x, y + 0.045, z, 0.0,
				SnakeAttackVolume.TAIL_INNER_RADIUS, SnakeAttackVolume.TAIL_OUTER_RADIUS,
				Math.PI, 0x0E0C09, 0x705B27, 0xF7E7B1, alpha * 0.42F);
		layeredVerticalArc(buffer, pose, x, z, center, 5.1, 0.8, y + 0.06, y + 1.55,
				0x14110C, 0xDDB74B, 0xFFFFFF, alpha);
		layeredVerticalArc(buffer, pose, x, z, center - direction * 0.22, 4.55, 0.56,
				y + 0.09, y + 0.86, 0x0A1820, 0x68CDE3, 0xE8FCFF, alpha * 0.7F);
		return true;
	}

	private static boolean renderGroundPoison(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double fx, double fz, int tick) {
		if (tick < SnakeKingAttackRules.GROUND_POISON_WARNING_START
				|| tick > SnakeKingAttackRules.GROUND_POISON_HIT_TICK + 2) {
			return false;
		}
		float charge = Math.min(1.0F,
				(tick - SnakeKingAttackRules.GROUND_POISON_WARNING_START)
						/ (float)(SnakeKingAttackRules.GROUND_POISON_HIT_TICK
								- SnakeKingAttackRules.GROUND_POISON_WARNING_START));
		fanFill(buffer, pose, x, y + 0.03, z, fx, fz, SnakeAttackVolume.GROUND_POISON_RANGE,
				SnakeAttackVolume.GROUND_POISON_HALF_ANGLE, 0x171006, 0x6B4612,
				0.32F + charge * 0.08F);
		layeredFanOutline(buffer, pose, x, y + 0.055, z, fx, fz,
				SnakeAttackVolume.GROUND_POISON_RANGE, SnakeAttackVolume.GROUND_POISON_HALF_ANGLE,
				0x1B1207, 0xD28A21, 0xFFF1A8, 0.9F + charge * 0.08F);
		renderFanGrid(buffer, pose, x, y + 0.07, z, fx, fz,
				SnakeAttackVolume.GROUND_POISON_RANGE, SnakeAttackVolume.GROUND_POISON_HALF_ANGLE,
				0x241707, 0xF0AD35, 0.58F + charge * 0.14F);
		if (tick >= SnakeKingAttackRules.GROUND_POISON_HIT_TICK) {
			double center = Math.atan2(fz, fx);
			double wave = SnakeAttackVolume.GROUND_POISON_RANGE
					* (tick - SnakeKingAttackRules.GROUND_POISON_HIT_TICK + 1) / 3.0;
			for (int i = 0; i < 3; i++) {
				double radius = Math.max(0.35, wave - i * 0.72);
				layeredArc(buffer, pose, x, y + 0.085 + i * 0.012, z, center,
						Math.max(0.1, radius - 0.16), radius + 0.16,
						SnakeAttackVolume.GROUND_POISON_HALF_ANGLE, 0x1B1207,
						i == 0 ? 0xFFD060 : 0xB86F18, 0xFFF8D8, 0.96F - i * 0.12F);
			}
		}
		return true;
	}

	private static boolean renderJudgment(BufferBuilder buffer, Matrix4f pose, VirtualSnakeKing snake,
			double x, double y, double z, int tick) {
		boolean wrote = tick >= 20 && tick <= 62 && judgment(buffer, pose, snake, x, y, z);
		if (tick >= 60 && tick <= 65) {
			double progress = (tick - 59) / 6.0;
			for (int i = 0; i < 3; i++) {
				double radius = Math.max(0.5, progress * 12.0 - i * 1.1);
				layeredArc(buffer, pose, x, y + 0.08 + i * 0.025, z, 0.0,
						Math.max(0.1, radius - 0.2), radius + 0.2, Math.PI,
						0x131008, i == 0 ? 0xE5BE43 : 0x8E6E20, 0xFFF4CE,
						0.98F - i * 0.14F);
			}
			wrote = true;
		}
		return wrote;
	}

	private static boolean renderOverheadImpact(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double fx, double fz, int tick) {
		if (tick < SnakeKingAttackRules.OVERHEAD_WARNING_START
				|| tick > SnakeKingAttackRules.OVERHEAD_HIT_TICK + 4) {
			return false;
		}
		double center = Math.atan2(fz, fx);
		float charge = Math.min(1.0F,
				(tick - SnakeKingAttackRules.OVERHEAD_WARNING_START)
						/ (float)(SnakeKingAttackRules.OVERHEAD_HIT_TICK
								- SnakeKingAttackRules.OVERHEAD_WARNING_START));
		double beamY = y + 1.8;
		fanFill(buffer, pose, x, y + 0.035, z, fx, fz, SnakeAttackVolume.OVERHEAD_RANGE,
				SnakeAttackVolume.OVERHEAD_HALF_ANGLE, 0x0D0D0A, 0x70642B,
				0.24F + charge * 0.1F);
		layeredFanOutline(buffer, pose, x, y + 0.06, z, fx, fz,
				SnakeAttackVolume.OVERHEAD_RANGE, SnakeAttackVolume.OVERHEAD_HALF_ANGLE,
				0x11100A, 0xB99C35, 0xFFF4C9, 0.78F + charge * 0.2F);
		layeredRibbon(buffer, pose, x, beamY, z, fx, fz, SnakeAttackVolume.OVERHEAD_RANGE,
				0.26, 0.0, 0x11100A, 0xCEB54D, 0xFFF7D2, 0.78F + charge * 0.2F);
		double radius = tick < SnakeKingAttackRules.OVERHEAD_HIT_TICK
				? 2.2 + charge * 1.8
				: 2.2 + (tick - SnakeKingAttackRules.OVERHEAD_HIT_TICK) * 1.2;
		layeredArc(buffer, pose, x, y + 0.075, z, center, Math.max(0.1, radius - 0.2),
				radius + 0.2, SnakeAttackVolume.OVERHEAD_HALF_ANGLE,
				0x11100A, 0xD9BF50, 0xFFFFFF,
				tick <= SnakeKingAttackRules.OVERHEAD_HIT_TICK
						? 0.72F + charge * 0.24F
						: Math.max(0.3F, 0.92F
								- (tick - SnakeKingAttackRules.OVERHEAD_HIT_TICK) * 0.14F));
		return true;
	}

	private static boolean isDashTelegraphVisible(int tick) {
		return tick >= SnakeKingAttackRules.SPECIAL_ANIMATION_START
				&& tick <= SnakeDashRules.FIRST_START + SnakeDashRules.ACTIVE_TICKS
				|| tick >= SnakeDashRules.SECOND_START - 12
				&& tick <= SnakeDashRules.SECOND_START + SnakeDashRules.ACTIVE_TICKS;
	}

	private static boolean judgment(BufferBuilder buffer, Matrix4f pose, VirtualSnakeKing snake,
			double x, double y, double z) {
		for (int i = 0; i < 8; i++) {
			if (i == snake.getJudgmentSafePath()) {
				continue;
			}
			double angle = Math.PI * 2.0 * i / 8.0;
			layeredRibbon(buffer, pose, x, y + 0.035, z, Math.cos(angle), Math.sin(angle),
					12.5, 0.34, 0.0, 0x130F08, 0xB7902D, 0xFFF1C5, 0.82F);
		}
		layeredArc(buffer, pose, x, y + 0.06, z, 0.0, 3.1, 3.48, Math.PI,
				0x120F08, 0xC39C35, 0xFFF2C9, 0.88F);
		layeredArc(buffer, pose, x, y + 0.065, z, 0.0, 7.5, 7.9, Math.PI,
				0x120F08, 0x8E7025, 0xFFEBC1, 0.82F);
		return true;
	}

	private static void layeredRibbon(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double fx, double fz, double length,
			double halfWidth, double lateralOffset, int dark, int main, int core, float alpha) {
		alpha = Math.max(0.0F, Math.min(1.0F, alpha));
		ribbon(buffer, pose, x, y, z, fx, fz, length, halfWidth * 1.34, lateralOffset,
				dark, dark, Math.min(1.0F, alpha * 0.94F));
		ribbon(buffer, pose, x, y + 0.006, z, fx, fz, length, halfWidth, lateralOffset,
				dark, main, Math.min(1.0F, alpha));
		ribbon(buffer, pose, x, y + 0.012, z, fx, fz, length, halfWidth * 0.24, lateralOffset,
				main, core, Math.min(1.0F, alpha * 0.92F));
	}

	private static void layeredArc(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double center, double inner, double outer,
			double halfAngle, int dark, int main, int core, float alpha) {
		alpha = Math.max(0.0F, Math.min(1.0F, alpha));
		double width = Math.max(0.04, outer - inner);
		arc(buffer, pose, x, y, z, center, Math.max(0.02, inner - width * 0.5),
				outer + width * 0.5, halfAngle, dark, dark, alpha * 0.94F);
		arc(buffer, pose, x, y + 0.006, z, center, inner, outer, halfAngle,
				dark, main, alpha);
		double coreInset = width * 0.35;
		arc(buffer, pose, x, y + 0.012, z, center, inner + coreInset,
				Math.max(inner + coreInset + 0.02, outer - coreInset), halfAngle,
				main, core, alpha * 0.9F);
	}

	private static void layeredVerticalArc(BufferBuilder buffer, Matrix4f pose,
			double x, double z, double center, double radius, double halfAngle,
			double minY, double maxY, int dark, int main, int core, float alpha) {
		alpha = Math.max(0.0F, Math.min(1.0F, alpha));
		verticalArc(buffer, pose, x, z, center, radius + 0.025, halfAngle + 0.065,
				minY - 0.04, maxY + 0.04, dark, dark, alpha * 0.94F);
		verticalArc(buffer, pose, x, z, center, radius, halfAngle,
				minY, maxY, dark, main, alpha);
		double heightInset = Math.max(0.05, (maxY - minY) * 0.34);
		verticalArc(buffer, pose, x, z, center, radius - 0.018, halfAngle * 0.9,
				minY + heightInset, maxY - heightInset, main, core, alpha * 0.9F);
	}

	private static float biteClosure(int tick, int impactTick) {
		if (tick <= impactTick) {
			return Math.max(0.0F, Math.min(1.0F, (tick - 3.0F) / (impactTick - 3.0F)));
		}
		return Math.max(0.0F, 1.0F - (tick - impactTick) / 4.0F);
	}

	private static void layeredFangMaw(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double fx, double fz, float closure,
			double halfWidth, double halfHeight, int dark, int main, int core, float alpha) {
		double sideX = -fz;
		double sideZ = fx;
		double jawGap = halfHeight * (1.0 - closure * 0.78);
		double tipGap = Math.max(0.05, jawGap * 0.12);
		double outer = halfWidth * (0.76 + (1.0 - closure) * 0.18);
		double inner = halfWidth * 0.28;

		layeredFang(buffer, pose, x, y, z, sideX, sideZ,
				-outer, jawGap, -inner, tipGap, dark, main, core, alpha);
		layeredFang(buffer, pose, x, y, z, sideX, sideZ,
				outer, jawGap, inner, tipGap, dark, main, core, alpha);
		layeredFang(buffer, pose, x, y, z, sideX, sideZ,
				-outer, -jawGap, -inner, -tipGap, dark, main, core, alpha);
		layeredFang(buffer, pose, x, y, z, sideX, sideZ,
				outer, -jawGap, inner, -tipGap, dark, main, core, alpha);

		double jawWidth = halfWidth * 0.92;
		lineRibbon(buffer, pose,
				x - sideX * jawWidth, y + jawGap, z - sideZ * jawWidth,
				x + sideX * jawWidth, z + sideZ * jawWidth,
				0.055, dark, main, alpha * 0.82F);
		lineRibbon(buffer, pose,
				x - sideX * jawWidth, y - jawGap, z - sideZ * jawWidth,
				x + sideX * jawWidth, z + sideZ * jawWidth,
				0.055, dark, main, alpha * 0.82F);
	}

	private static void layeredFang(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double sideX, double sideZ,
			double baseOffset, double baseY, double tipOffset, double tipY,
			int dark, int main, int core, float alpha) {
		double baseHalf = 0.25;
		triangle(buffer, pose,
				x + sideX * (baseOffset - baseHalf), y + baseY, z + sideZ * (baseOffset - baseHalf),
				x + sideX * (baseOffset + baseHalf), y + baseY, z + sideZ * (baseOffset + baseHalf),
				x + sideX * tipOffset, y + tipY, z + sideZ * tipOffset,
				dark, dark, alpha * 0.96F);
		triangle(buffer, pose,
				x + sideX * (baseOffset - baseHalf * 0.72), y + baseY * 0.985,
				z + sideZ * (baseOffset - baseHalf * 0.72),
				x + sideX * (baseOffset + baseHalf * 0.72), y + baseY * 0.985,
				z + sideZ * (baseOffset + baseHalf * 0.72),
				x + sideX * tipOffset, y + tipY, z + sideZ * tipOffset,
				main, core, alpha);
	}

	private static void layeredFanOutline(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double fx, double fz, double range, double halfAngle,
			int dark, int main, int core, float alpha) {
		double center = Math.atan2(fz, fx);
		double left = center - halfAngle;
		double right = center + halfAngle;
		layeredRibbon(buffer, pose, x, y, z, Math.cos(left), Math.sin(left), range,
				0.12, 0.0, dark, main, core, alpha);
		layeredRibbon(buffer, pose, x, y, z, Math.cos(right), Math.sin(right), range,
				0.12, 0.0, dark, main, core, alpha);
		layeredArc(buffer, pose, x, y, z, center, range - 0.17, range + 0.17,
				halfAngle, dark, main, core, alpha);
	}

	private static void fanFill(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double fx, double fz, double range, double halfAngle,
			int dark, int bright, float alpha) {
		double center = Math.atan2(fz, fx);
		int steps = 18;
		for (int i = 0; i < steps; i++) {
			double a0 = center - halfAngle + halfAngle * 2.0 * i / steps;
			double a1 = center - halfAngle + halfAngle * 2.0 * (i + 1) / steps;
			triangle(buffer, pose, x, y, z,
					x + Math.cos(a0) * range, y, z + Math.sin(a0) * range,
					x + Math.cos(a1) * range, y, z + Math.sin(a1) * range,
					dark, bright, alpha);
		}
	}

	private static void renderFanGrid(BufferBuilder buffer, Matrix4f pose,
			double x, double y, double z, double fx, double fz, double range, double halfAngle,
			int dark, int bright, float alpha) {
		double center = Math.atan2(fz, fx);
		for (int i = -2; i <= 2; i++) {
			double angle = center + halfAngle * i / 2.0;
			lineRibbon(buffer, pose, x, y, z,
					x + Math.cos(angle) * range, z + Math.sin(angle) * range,
					0.045, dark, bright, alpha);
		}
		for (int ring = 1; ring <= 3; ring++) {
			double radius = range * ring / 3.0;
			arc(buffer, pose, x, y + ring * 0.003, z, center, radius - 0.045,
					radius + 0.045, halfAngle, dark, bright, alpha * 0.86F);
		}
	}

	private static void lineRibbon(BufferBuilder buffer, Matrix4f pose, double x0, double y, double z0,
			double x1, double z1, double halfWidth, int dark, int bright, float alpha) {
		double dx = x1 - x0;
		double dz = z1 - z0;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-5) {
			return;
		}
		double sx = -dz / length * halfWidth;
		double sz = dx / length * halfWidth;
		quad(buffer, pose, x0 - sx, y, z0 - sz, x1 - sx, y, z1 - sz,
				x1 + sx, y, z1 + sz, x0 + sx, y, z0 + sz, dark, bright, alpha);
	}

	private static void verticalRibbon(BufferBuilder buffer, Matrix4f pose, double x, double minY,
			double maxY, double z, double fx, double fz, double length, double halfWidth,
			double lateralOffset, int dark, int bright, float alpha) {
		double sx = -fz;
		double sz = fx;
		double cx = x + sx * lateralOffset;
		double cz = z + sz * lateralOffset;
		double ex = cx + fx * length;
		double ez = cz + fz * length;
		quad(buffer, pose, cx, minY, cz, ex, minY, ez, ex, maxY, ez, cx, maxY, cz,
				dark, bright, alpha);
		if (halfWidth > 0.0) {
			quad(buffer, pose, cx - sx * halfWidth, minY, cz - sz * halfWidth,
					ex - sx * halfWidth, minY, ez - sz * halfWidth,
					ex + sx * halfWidth, minY, ez + sz * halfWidth,
					cx + sx * halfWidth, minY, cz + sz * halfWidth, dark, bright, alpha * 0.45F);
		}
	}

	private static void verticalArc(BufferBuilder buffer, Matrix4f pose, double x, double z,
			double center, double radius, double halfAngle, double minY, double maxY,
			int dark, int bright, float alpha) {
		int steps = 14;
		for (int i = 0; i < steps; i++) {
			double a0 = center - halfAngle + halfAngle * 2.0 * i / steps;
			double a1 = center - halfAngle + halfAngle * 2.0 * (i + 1) / steps;
			double x0 = x + Math.cos(a0) * radius;
			double z0 = z + Math.sin(a0) * radius;
			double x1 = x + Math.cos(a1) * radius;
			double z1 = z + Math.sin(a1) * radius;
			quad(buffer, pose, x0, minY, z0, x1, minY, z1, x1, maxY, z1, x0, maxY, z0,
					dark, bright, alpha);
		}
	}

	private static boolean arc(BufferBuilder buffer, Matrix4f pose, double x, double y, double z,
			double center, double inner, double outer, double halfAngle, int dark, int bright, float alpha) {
		int steps = 10;
		for (int i = 0; i < steps; i++) {
			double a0 = center - halfAngle + halfAngle * 2.0 * i / steps;
			double a1 = center - halfAngle + halfAngle * 2.0 * (i + 1) / steps;
			quad(buffer, pose, x + Math.cos(a0) * inner, y, z + Math.sin(a0) * inner,
					x + Math.cos(a0) * outer, y, z + Math.sin(a0) * outer,
					x + Math.cos(a1) * outer, y, z + Math.sin(a1) * outer,
					x + Math.cos(a1) * inner, y, z + Math.sin(a1) * inner, dark, bright, alpha);
		}
		return true;
	}

	private static void ribbon(BufferBuilder buffer, Matrix4f pose, double x, double y, double z,
			double fx, double fz, double length, double halfWidth, double lateralOffset,
			int dark, int bright, float alpha) {
		double sx = -fz;
		double sz = fx;
		double cx = x + sx * lateralOffset;
		double cz = z + sz * lateralOffset;
		quad(buffer, pose, cx - sx * halfWidth, y, cz - sz * halfWidth,
				cx + fx * length - sx * halfWidth, y, cz + fz * length - sz * halfWidth,
				cx + fx * length + sx * halfWidth, y, cz + fz * length + sz * halfWidth,
				cx + sx * halfWidth, y, cz + sz * halfWidth, dark, bright, alpha);
	}

	private static void quad(BufferBuilder buffer, Matrix4f pose,
			double x0, double y0, double z0, double x1, double y1, double z1,
			double x2, double y2, double z2, double x3, double y3, double z3,
			int dark, int bright, float alpha) {
		vertex(buffer, pose, x0, y0, z0, dark, alpha * 0.68F, 0.0F, 0.0F);
		vertex(buffer, pose, x1, y1, z1, bright, alpha, 1.0F, 0.0F);
		vertex(buffer, pose, x2, y2, z2, bright, alpha, 1.0F, 1.0F);
		vertex(buffer, pose, x0, y0, z0, dark, alpha * 0.68F, 0.0F, 0.0F);
		vertex(buffer, pose, x2, y2, z2, bright, alpha, 1.0F, 1.0F);
		vertex(buffer, pose, x3, y3, z3, dark, alpha * 0.68F, 0.0F, 1.0F);
	}

	private static void triangle(BufferBuilder buffer, Matrix4f pose,
			double x0, double y0, double z0, double x1, double y1, double z1,
			double x2, double y2, double z2, int dark, int bright, float alpha) {
		vertex(buffer, pose, x0, y0, z0, dark, alpha * 0.68F, 0.0F, 0.5F);
		vertex(buffer, pose, x1, y1, z1, bright, alpha, 1.0F, 0.0F);
		vertex(buffer, pose, x2, y2, z2, bright, alpha, 1.0F, 1.0F);
	}

	private static void vertex(BufferBuilder buffer, Matrix4f pose, double x, double y, double z,
			int rgb, float alpha, float u, float v) {
		buffer.addVertex(pose, (float)x, (float)y, (float)z).setColor(
				(rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F,
				(rgb & 255) / 255.0F, alpha).setUv(u, v);
	}
}
