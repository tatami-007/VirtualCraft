package dev.tatami.virtualcraft.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Constructor parameters for client-only extensible enums. */
public final class VirtualCraftClientEnumParams {
	public static final EnumProxy<HumanoidModel.ArmPose> VIRTUAL_SWORD_BLOCK = new EnumProxy<>(
			HumanoidModel.ArmPose.class,
			false,
			(IArmPoseTransformer) VirtualCraftClientEnumParams::applySwordBlockPose);

	private VirtualCraftClientEnumParams() {
	}

	private static void applySwordBlockPose(HumanoidModel<?> model,
			LivingEntity entity, HumanoidArm arm) {
		var blockingArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;

		// Anchor the guard to the torso instead of adding head pitch/yaw like BLOCK.
		blockingArm.xRot = -1.18F;
		blockingArm.yRot = side * -0.48F;
		blockingArm.zRot = side * -0.10F;
	}
}
