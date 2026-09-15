package dev.tatami.virtualcraft.client.model;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.boss.SnakeHuntPattern;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public final class VirtualSnakeKingGeoModel extends GeoModel<VirtualSnakeKing> {
	private static final double VISUAL_PREDICTION_TICKS = 0.75;
	private static final ResourceLocation MODEL = id("geo/virtual_snake_king.geo.json");
	private static final ResourceLocation TEXTURE = id("textures/entity/snake_king/virtual_snake_king.png");
	private static final ResourceLocation ANIMATION = id("animations/virtual_snake_king.animation.json");
	private static final String[] SWEEP_BONES = {
			"body_2", "body_3", "body_4", "body_5", "body_6", "tail_1", "tail_2", "tail_3"
	};

	@Override
	public ResourceLocation getModelResource(VirtualSnakeKing snake) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(VirtualSnakeKing snake) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(VirtualSnakeKing snake) {
		return ANIMATION;
	}

	@Override
	public void setCustomAnimations(VirtualSnakeKing snake, long instanceId,
			AnimationState<VirtualSnakeKing> animationState) {
		super.setCustomAnimations(snake, instanceId, animationState);
		SweepPose sweep = sweepPose(snake, animationState.getPartialTick());
		if (sweep == null) {
			return;
		}
		float direction = SnakeHuntPattern.tailSweepDirection(snake.isSweepLeft());
		GeoBone rootBone = getBone("snake_root").orElse(null);
		if (rootBone != null) {
			rootBone.setRotY(direction * sweep.spin());
		}
		float[] absoluteAngles = {
				sweep.root() * 0.08F,
				sweep.root() * 0.18F,
				sweep.root() * 0.31F,
				sweep.root() * 0.46F,
				sweep.root() * 0.62F,
				sweep.root(),
				sweep.middle(),
				sweep.tip()
		};
		float parentAngle = 0.0F;
		for (int index = 0; index < SWEEP_BONES.length; index++) {
			GeoBone bone = getBone(SWEEP_BONES[index]).orElse(null);
			if (bone != null) {
				bone.setRotY(direction * (absoluteAngles[index] - parentAngle));
			}
			parentAngle = absoluteAngles[index];
		}
	}

	private static SweepPose sweepPose(VirtualSnakeKing snake, float partialTick) {
		int attack = snake.getAttackState();
		double visualTick = snake.getVisualAttackTick(partialTick) + VISUAL_PREDICTION_TICKS;
		if (attack == VirtualSnakeKing.ATTACK_TAIL_SWEEP) {
			return sweepOrRecovery(visualTick);
		}
		if (attack != VirtualSnakeKing.ATTACK_DOUBLE_HUNT
				|| SnakeHuntPattern.isComboOpening((int)Math.floor(visualTick))) {
			return null;
		}
		SnakeHuntPattern pattern = snake.getHuntPattern();
		int segment = pattern.comboSegmentAt((int)Math.floor(visualTick));
		if (segment >= pattern.segmentCount()) {
			return null;
		}
		double localTick = visualTick - pattern.comboSegmentStartTick(segment);
		if (pattern.move(segment) == SnakeHuntPattern.Move.TAIL) {
			return activeSweep(localTick);
		}
		if (segment > 0 && pattern.move(segment - 1) == SnakeHuntPattern.Move.TAIL
				&& localTick <= 5.0) {
			return recoveryPose(localTick);
		}
		return null;
	}

	private static SweepPose sweepOrRecovery(double tick) {
		if (tick <= SnakeHuntPattern.TAIL_HIT_TICK) {
			return activeSweep(tick);
		}
		if (tick <= SnakeHuntPattern.TAIL_HIT_TICK + 5.0) {
			return recoveryPose(tick - SnakeHuntPattern.TAIL_HIT_TICK);
		}
		return null;
	}

	private static SweepPose activeSweep(double tick) {
		SnakeHuntPattern.TailPose pose = SnakeHuntPattern.tailPose(tick);
		return new SweepPose(
				(float)SnakeHuntPattern.tailBodySpinPhase(tick),
				(float)pose.root(),
				(float)pose.middle(),
				(float)pose.tip());
	}

	private static SweepPose recoveryPose(double recoveryTick) {
		SnakeHuntPattern.TailPose pose = SnakeHuntPattern.tailRecoveryPose(recoveryTick);
		// Keep the completed revolution numerically continuous. Zero and 2π have
		// the same visible orientation, but jumping between them can make an
		// animation controller interpolate through an unwanted reverse spin.
		return new SweepPose((float)(Math.PI * 2.0),
				(float)pose.root(), (float)pose.middle(), (float)pose.tip());
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, path);
	}

	private record SweepPose(float spin, float root, float middle, float tip) {
	}
}
