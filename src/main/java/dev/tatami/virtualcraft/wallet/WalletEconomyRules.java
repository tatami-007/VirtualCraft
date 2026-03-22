package dev.tatami.virtualcraft.wallet;

import dev.tatami.virtualcraft.currency.CryptoCurrency;

public final class WalletEconomyRules {
	public static final long SWAP_GAS_MINOR = CryptoCurrency.SOL.parse("0.02").orElseThrow();
	public static final long TRANSFER_GAS_MINOR = CryptoCurrency.SOL.parse("0.01").orElseThrow();
	public static final long PREMIUM_BUY_GAS_MINOR = CryptoCurrency.ETH.parse("0.005").orElseThrow();

	private WalletEconomyRules() {
	}
}
