package dev.tatami.virtualcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.tatami.virtualcraft.item.VirtualSwordItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Stable pre-1.9-style sword block pose for VirtualCraft swords. */
public final class VirtualSwordClientExtensions implements IClientItemExtensions {
	public static final VirtualSwordClientExtensions INSTANCE = new VirtualSwordClientExtensions();

	private VirtualSwordClientExtensions() {
	}

	@Override
	public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand,
			ItemStack stack) {
		return entity.isUsingItem() && entity.getUseItem() == stack
				&& stack.getItem() instanceof VirtualSwordItem
						? VirtualCraftClientEnumParams.VIRTUAL_SWORD_BLOCK.getValue()
						: null;
	}

	@Override
	public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player,
			HumanoidArm arm, ItemStack stack, float partialTick, float equipProcess,
			float swingProcess) {
		if (!(stack.getItem() instanceof VirtualSwordItem)
				|| !player.isUsingItem() || player.getUseItem() != stack) {
			return false;
		}

		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		// Lock the equip dip while blocking so a parry cannot reset the hand for one frame.
		poseStack.translate(side * 0.56F, -0.52F, -0.72F);
		poseStack.translate(side * -0.14142136F, 0.08F, 0.14142136F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-102.25F));
		poseStack.mulPose(Axis.YP.rotationDegrees(side * 13.365F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 78.05F));
		return true;
	}
}
