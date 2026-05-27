package dev.tatami.virtualcraft.item;

import java.util.List;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class HashVoucherItem extends Item {
	public static final long BASIC_BTC_PAYOUT_MINOR = CryptoCurrency.BTC.parse("0.000300").orElseThrow();
	public static final long EXPLORER_BTC_PAYOUT_MINOR = CryptoCurrency.BTC.parse("0.001000").orElseThrow();
	public static final long ENCRYPTED_BTC_PAYOUT_MINOR = CryptoCurrency.BTC.parse("0.002500").orElseThrow();
	public static final long QUANTUM_BTC_PAYOUT_MINOR = CryptoCurrency.BTC.parse("0.010000").orElseThrow();
	public static final long GENESIS_BTC_PAYOUT_MINOR = CryptoCurrency.BTC.parse("0.030000").orElseThrow();
	/** Kept for older call-sites; new code should read the value from the item instance. */
	public static final long BTC_PAYOUT_MINOR = BASIC_BTC_PAYOUT_MINOR;
	private final long btcPayoutMinor;

	public HashVoucherItem() {
		this(BASIC_BTC_PAYOUT_MINOR);
	}

	public HashVoucherItem(long btcPayoutMinor) {
		super(new Item.Properties().stacksTo(64));
		this.btcPayoutMinor = Math.max(0L, btcPayoutMinor);
	}

	public long btcPayoutMinor() {
		return btcPayoutMinor;
	}

	public static boolean isHashVoucher(ItemStack stack) {
		return stack.getItem() instanceof HashVoucherItem;
	}

	public static long btcPayoutMinor(ItemStack stack) {
		return stack.getItem() instanceof HashVoucherItem voucher ? voucher.btcPayoutMinor() : 0L;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.hash_voucher.value",
				CryptoCurrency.BTC.formatCompact(btcPayoutMinor),
				CryptoCurrency.BTC.ticker()));
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.hash_voucher.use"));
	}
}
