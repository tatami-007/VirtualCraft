package dev.tatami.virtualcraft.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 电脑物品（虚拟世界维度正在重构为科幻大世界，传送功能暂时停用）。
 */
public class ComputerItem extends Item {
	public ComputerItem() {
		super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (!level.isClientSide) {
			player.displayClientMessage(
					Component.translatable("message.virtual_craft.computer.rebuilding")
							.withStyle(ChatFormatting.AQUA),
					true);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
		tooltip.add(Component.translatable("tooltip.virtual_craft.computer.rebuilding").withStyle(ChatFormatting.GRAY));
	}
}
