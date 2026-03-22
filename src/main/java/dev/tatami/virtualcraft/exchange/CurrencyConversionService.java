package dev.tatami.virtualcraft.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;

import dev.tatami.virtualcraft.currency.CryptoCurrency;

public final class CurrencyConversionService {
	private CurrencyConversionService() {
	}

	public static BigDecimal getQuoteToUsdt(CryptoCurrency currency, ExchangeRateSnapshot snapshot) {
		return snapshot.quoteToUsdt(currency);
	}

	public static boolean canWithdrawWholeItem(CryptoCurrency currency, long minorUnits) {
		return minorUnits >= currency.wholeUnitMinor();
	}

	public static long convert(CryptoCurrency source, CryptoCurrency target, long amountMinor, ExchangeRateSnapshot snapshot) {
		return convert(source, target, amountMinor, snapshot, RoundingMode.DOWN);
	}

	public static long convert(CryptoCurrency source, CryptoCurrency target, long amountMinor, ExchangeRateSnapshot snapshot, RoundingMode roundingMode) {
		if (amountMinor <= 0) {
			return 0L;
		}
		BigDecimal sourceAmount = source.toDecimal(amountMinor);
		BigDecimal sourceValueUsdt = sourceAmount.multiply(getQuoteToUsdt(source, snapshot));
		BigDecimal targetAmount = sourceValueUsdt.divide(getQuoteToUsdt(target, snapshot), target.scale(), roundingMode);
		return target.toMinorUnits(targetAmount, roundingMode);
	}

	public static long convertUsdtMinorTo(CryptoCurrency target, long usdtMinor, ExchangeRateSnapshot snapshot, RoundingMode roundingMode) {
		return convert(CryptoCurrency.USDT, target, usdtMinor, snapshot, roundingMode);
	}

	public static long totalWalletValueUsdtMinor(long[] balances, ExchangeRateSnapshot snapshot) {
		long total = 0L;
		CryptoCurrency[] currencies = CryptoCurrency.values();
		for (int index = 0; index < Math.min(currencies.length, balances.length); index++) {
			total = Math.addExact(total, convert(currencies[index], CryptoCurrency.USDT, balances[index], snapshot, RoundingMode.DOWN));
		}
		return total;
	}
}
