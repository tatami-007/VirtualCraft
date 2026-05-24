package dev.tatami.virtualcraft.wallet;

import dev.tatami.virtualcraft.currency.CryptoCurrency;

public final class WalletEconomyRules {
	/**
	 * 转账链上手续费货币（用户要求：手续费用波场币 TRX）。
	 *
	 * <p>之前用 SOL 0.01（≈ 1 美分），切到 TRX 后用 1 TRX
	 * （TRX 单价 ≈ $0.1，1 TRX ≈ 1 角，价值数量级与原 SOL gas 接近、用户记忆负担小）。</p>
	 */
	public static final CryptoCurrency TRANSFER_GAS_CURRENCY = CryptoCurrency.TRX;
	public static final long TRANSFER_GAS_MINOR = CryptoCurrency.TRX.parse("1").orElseThrow();

	private WalletEconomyRules() {
	}
}
