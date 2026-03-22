package dev.tatami.virtualcraft.item;

import java.util.List;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class HashVoucherItem extends Item {
	public static final long BTC_PAYOUT_MINOR = CryptoCurrency.BTC.parse("0.000010").orElseThrow();

	public HashVoucherItem() {
		super(new Item.Properties().stacksTo(64));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.hash_voucher.value",
				CryptoCurrency.BTC.formatCompact(BTC_PAYOUT_MINOR),
				CryptoCurrency.BTC.ticker()));
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.hash_voucher.use"));
	}
}
