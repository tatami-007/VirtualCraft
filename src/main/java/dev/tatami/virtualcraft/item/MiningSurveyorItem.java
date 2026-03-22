package dev.tatami.virtualcraft.item;

import java.util.List;

import dev.tatami.virtualcraft.mining.MiningSurveyTargetData;
import dev.tatami.virtualcraft.util.OreClusterFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class MiningSurveyorItem extends Item {
	private static final int HORIZONTAL_RANGE = 24;
	private static final int VERTICAL_RANGE = 18;
	private static final int MAX_CLUSTER_SIZE = 128;

	public MiningSurveyorItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (player.isShiftKeyDown()) {
			MiningSurveyTargetData.clear(stack);
			player.displayClientMessage(Component.translatable("message.virtual_craft.mining_surveyor.clear"), true);
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		if (level.isClientSide) {
			return InteractionResultHolder.success(stack);
		}

		OreClusterFinder.SurveyResult result = OreClusterFinder.findSurveyTarget(level, player.blockPosition(), HORIZONTAL_RANGE, VERTICAL_RANGE, MAX_CLUSTER_SIZE);
		if (result == null) {
			MiningSurveyTargetData.clear(stack);
			player.displayClientMessage(Component.translatable("message.virtual_craft.mining_surveyor.none"), true);
			return InteractionResultHolder.success(stack);
		}

		MiningSurveyTargetData.setTarget(stack, result.anchor(), result.clusterSize());
		BlockPos target = result.anchor();
		player.displayClientMessage(Component.translatable("message.virtual_craft.mining_surveyor.found", result.clusterSize(), target.getX(), target.getY(), target.getZ()), true);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.use"));
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.clear"));
		MiningSurveyTargetData.target(stack).ifPresent(target -> {
			tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.target", target.getX(), target.getY(), target.getZ()));
			tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.cluster", MiningSurveyTargetData.clusterSize(stack)));
		});
	}
}
