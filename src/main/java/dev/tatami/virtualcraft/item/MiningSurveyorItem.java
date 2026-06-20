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
		// Practicality-first interaction: a bare right-click is the high-frequency
		// action while prospecting, so it re-scans the currently selected ore in
		// place (no GUI). Sneak+right-click opens the picker to switch ore / clear.
		if (level.isClientSide) {
			if (player.isShiftKeyDown()) {
				ClientHooks.openSurveyorScreen(stack);
			} else {
				ClientHooks.requestRescan(stack);
			}
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		MiningSurveyTarget selected = MiningSurveyTargetData.selectedTarget(stack);
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.rescan"));
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.open"));
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.mining_surveyor.selected", selected.name()));
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

		private static void requestRescan(ItemStack stack) {
			String key = MiningSurveyTargetData.selectedTarget(stack).key();
			net.neoforged.neoforge.network.PacketDistributor.sendToServer(
					new dev.tatami.virtualcraft.network.MiningSurveyorActionPayload(key, false));
		}
	}
}
