package dev.tatami.virtualcraft.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import dev.tatami.virtualcraft.currency.CryptoCurrency;

public final class ExchangeRateSnapshot {
	public enum SourceStatus {
		LIVE,
		CACHED,
		DEFAULT,
		STALE
	}

	private final EnumMap<CryptoCurrency, BigDecimal> quoteToUsdt;
	private final long updatedAtEpochSeconds;
	private final SourceStatus status;

	public ExchangeRateSnapshot(Map<CryptoCurrency, BigDecimal> quoteToUsdt, long updatedAtEpochSeconds, SourceStatus status) {
		this.quoteToUsdt = new EnumMap<>(CryptoCurrency.class);
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			BigDecimal quote = quoteToUsdt.get(currency);
			if (quote == null || quote.signum() <= 0 || currency.isUsdtPegged()) {
				quote = BigDecimal.ONE;
			}
			this.quoteToUsdt.put(currency, quote.max(BigDecimal.ONE.scaleByPowerOfTen(-currency.scale())));
		}
		this.updatedAtEpochSeconds = updatedAtEpochSeconds;
		this.status = status;
	}

	public BigDecimal quoteToUsdt(CryptoCurrency currency) {
		return quoteToUsdt.getOrDefault(currency, BigDecimal.ONE);
	}

	public EnumMap<CryptoCurrency, BigDecimal> quotes() {
		return new EnumMap<>(quoteToUsdt);
	}

	public long updatedAtEpochSeconds() {
		return updatedAtEpochSeconds;
	}

	public SourceStatus status() {
		return status;
	}

	public String updatedAtDisplay() {
		return Instant.ofEpochSecond(updatedAtEpochSeconds).toString();
	}

	public ExchangeRateSnapshot withStatus(SourceStatus newStatus) {
		return new ExchangeRateSnapshot(quoteToUsdt, updatedAtEpochSeconds, newStatus);
	}

	public static ExchangeRateSnapshot defaultSnapshot() {
		EnumMap<CryptoCurrency, BigDecimal> quotes = new EnumMap<>(CryptoCurrency.class);
		quotes.put(CryptoCurrency.BTC, new BigDecimal("84000.00"));
		quotes.put(CryptoCurrency.ETH, new BigDecimal("2100.00"));
		quotes.put(CryptoCurrency.USDT, BigDecimal.ONE.setScale(2, RoundingMode.DOWN));
		quotes.put(CryptoCurrency.SOL, new BigDecimal("130.00"));
		quotes.put(CryptoCurrency.TRX, new BigDecimal("0.22"));
		quotes.put(CryptoCurrency.DOGE, new BigDecimal("0.18"));
		return new ExchangeRateSnapshot(quotes, Instant.now().getEpochSecond(), SourceStatus.DEFAULT);
	}
}
