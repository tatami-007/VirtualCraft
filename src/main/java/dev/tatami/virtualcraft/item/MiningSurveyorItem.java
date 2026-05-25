package dev.tatami.virtualcraft.item;

import java.util.List;

import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.mining.MiningSurveyTargetData;
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
	public MiningSurveyorItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide) {
				MiningSurveyTargetData.clearLock(stack);
				player.displayClientMessage(Component.translatable("message.virtual_craft.mining_surveyor.clear"), true);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		if (level.isClientSide) {
			ClientHooks.openSurveyorScreen(stack);
			return InteractionResultHolder.success(stack);
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		MiningSurveyTarget selected = MiningSurveyTargetData.selectedTarget(stack);
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.use"));
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.selected", selected.name()));
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.clear"));
		MiningSurveyTargetData.target(stack).ifPresent(target -> {
			tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.target", target.getX(), target.getY(), target.getZ()));
			tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.cluster", MiningSurveyTargetData.clusterSize(stack)));
		});
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static final class ClientHooks {
		private static void openSurveyorScreen(ItemStack stack) {
			icyllis.modernui.mc.MuiModApi.openScreen(new dev.tatami.virtualcraft.client.screen.MiningSurveyorFragment(stack));
		}
	}
}
