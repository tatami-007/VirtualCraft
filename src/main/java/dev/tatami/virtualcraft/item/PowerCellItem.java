package dev.tatami.virtualcraft.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class PowerCellItem extends Item {
	private final Tier tier;

	public PowerCellItem(Tier tier) {
		super(new Item.Properties().stacksTo(1));
		this.tier = tier;
	}

	public Tier tier() {
		return tier;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.virtual_craft.power_cell.multiplier", tier.multiplier()).withStyle(ChatFormatting.GRAY));
	}

	public static boolean isPowerCell(ItemStack stack) {
		return stack.getItem() instanceof PowerCellItem;
	}

	public static int multiplier(ItemStack stack) {
		return stack.getItem() instanceof PowerCellItem cell ? cell.tier.multiplier() : 0;
	}

	public enum Tier {
		BASIC(1),
		ADVANCED(2),
		QUANTUM(3);

		private final int multiplier;

		Tier(int multiplier) {
			this.multiplier = multiplier;
		}

		public int multiplier() {
			return multiplier;
		}
	}
}
