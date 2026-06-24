package dev.tatami.virtualcraft.client.screen.holographic;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side market data backing the holographic terminal's MARKET (K-line)
 * page. Mirrors the interaction model of professional exchanges (Binance / OKX):
 *
 * <ul>
 *   <li>Real candlestick history per trading interval, seeded asynchronously
 *       from Binance Vision's public {@code /klines} REST endpoint so the chart
 *       is populated the instant the page opens instead of slowly accreting one
 *       candle per minute from live ticks.</li>
 *   <li>A continuously updated <em>current</em> candle: every live price tick
 *       pushed from the server ({@link #updateLivePrice}) folds into the open
 *       candle of every interval, so the most recent bar grows / shrinks in real
 *       time exactly like a live exchange chart.</li>
 *   <li>Multiple selectable intervals (1m / 5m / 15m / 1h / 4h / 1d).</li>
 * </ul>
 *
 * <p>All state is process-wide and keyed by {@code currency|interval}; the data
 * survives terminal re-opens. Network access is best-effort: if the REST seed
 * fails (offline / blocked), the chart still builds purely from accumulated live
 * ticks, so the page degrades gracefully.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HolographicMarketData {
	private HolographicMarketData() {
	}

	/** Candle aggregation windows offered in the UI, mapped to Binance kline codes. */
	public enum Interval {
		M1("1m", 60_000L),
		M5("5m", 300_000L),
		M15("15m", 900_000L),
		H1("1h", 3_600_000L),
		H4("4h", 14_400_000L),
		D1("1d", 86_400_000L);

		private final String code;
		private final long periodMs;

		Interval(String code, long periodMs) {
			this.code = code;
			this.periodMs = periodMs;
		}

		public String code() {
			return code;
		}

		public long periodMs() {
			return periodMs;
		}
	}

	public record Candle(long openTime, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
		public boolean isUp() {
			return close.compareTo(open) >= 0;
		}
	}

	private static final String KLINES_REST = "https://data-api.binance.vision/api/v3/klines";
	private static final int HISTORY_LIMIT = 150;
	private static final int MAX_CANDLES = 240;
	/** Re-pull REST history for a key at most this often (live ticks keep it fresh between pulls). */
	private static final long HISTORY_TTL_MS = 5 * 60_000L;

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.build();

	private static final Map<String, List<Candle>> CANDLES = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
	private static final Map<String, Long> LAST_FETCH_MS = new ConcurrentHashMap<>();
	private static final Map<String, AtomicLong> VERSION = new ConcurrentHashMap<>();

	private static String key(CryptoCurrency currency, Interval interval) {
		return currency.name() + "|" + interval.name();
	}

	private static String symbol(CryptoCurrency currency) {
		return currency.ticker() + "USDT";
	}

	private static AtomicLong version(String key) {
		return VERSION.computeIfAbsent(key, k -> new AtomicLong());
	}

	/** Monotonic counter; the UI compares it to know when to repaint a chart. */
	public static long dataVersion(CryptoCurrency currency, Interval interval) {
		return version(key(currency, interval)).get();
	}

	public static boolean isLoading(CryptoCurrency currency, Interval interval) {
		return LOADING.getOrDefault(key(currency, interval), Boolean.FALSE);
	}

	/** Snapshot copy (oldest first) of the candles known for the pair / interval. */
	public static List<Candle> getCandles(CryptoCurrency currency, Interval interval) {
		List<Candle> list = CANDLES.get(key(currency, interval));
		if (list == null) {
			return List.of();
		}
		synchronized (list) {
			return new ArrayList<>(list);
		}
	}

	/**
	 * Kicks off (at most one in-flight) async REST history pull for the pair /
	 * interval when the cached data is missing or older than {@link #HISTORY_TTL_MS}.
	 */
	public static void ensureHistory(CryptoCurrency currency, Interval interval) {
		if (currency == null || currency.isUsdtPegged()) {
			return;
		}
		String key = key(currency, interval);
		long now = System.currentTimeMillis();
		if (LOADING.getOrDefault(key, Boolean.FALSE)) {
			return;
		}
		long last = LAST_FETCH_MS.getOrDefault(key, 0L);
		if (last != 0L && now - last < HISTORY_TTL_MS) {
			return;
		}
		LOADING.put(key, Boolean.TRUE);
		LAST_FETCH_MS.put(key, now);

		String url = KLINES_REST + "?symbol=" + symbol(currency)
				+ "&interval=" + interval.code() + "&limit=" + HISTORY_LIMIT;
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(8))
				.header("Accept", "application/json")
				.GET()
				.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					try {
						if (response.statusCode() >= 200 && response.statusCode() < 300) {
							List<Candle> parsed = parseKlines(response.body());
							if (!parsed.isEmpty()) {
								storeHistory(key, parsed);
							}
						}
					} catch (Exception e) {
						VirtualCraft.LOGGER.debug("Kline history parse failed for {}", url, e);
					} finally {
						LOADING.put(key, Boolean.FALSE);
					}
				})
				.exceptionally(ex -> {
					LOADING.put(key, Boolean.FALSE);
					VirtualCraft.LOGGER.debug("Kline history fetch failed for {}", url, ex);
					return null;
				});
	}

	private static List<Candle> parseKlines(String body) {
		JsonArray rows = JsonParser.parseString(body).getAsJsonArray();
		List<Candle> out = new ArrayList<>(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			JsonArray row = rows.get(i).getAsJsonArray();
			long openTime = row.get(0).getAsLong();
			BigDecimal open = new BigDecimal(row.get(1).getAsString());
			BigDecimal high = new BigDecimal(row.get(2).getAsString());
			BigDecimal low = new BigDecimal(row.get(3).getAsString());
			BigDecimal close = new BigDecimal(row.get(4).getAsString());
			out.add(new Candle(openTime, open, high, low, close));
		}
		return out;
	}

	private static void storeHistory(String key, List<Candle> history) {
		List<Candle> list = CANDLES.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
		synchronized (list) {
			// Preserve any live-tick candle newer than the REST window's last bar
			// so a slow REST response can't roll the live candle backwards.
			Candle liveTail = list.isEmpty() ? null : list.get(list.size() - 1);
			long lastHistTime = history.get(history.size() - 1).openTime();
			list.clear();
			list.addAll(history);
			if (liveTail != null && liveTail.openTime() > lastHistTime) {
				list.add(liveTail);
			}
			trim(list);
		}
		version(key).incrementAndGet();
	}

	/**
	 * Folds a fresh spot price into the live candle of every interval for the
	 * given currency, appending a new candle when a period boundary is crossed.
	 */
	public static void updateLivePrice(CryptoCurrency currency, BigDecimal price) {
		if (currency == null || price == null || currency.isUsdtPegged() || price.signum() <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Interval interval : Interval.values()) {
			String key = key(currency, interval);
			List<Candle> list = CANDLES.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
			long periodStart = (now / interval.periodMs()) * interval.periodMs();
			synchronized (list) {
				Candle last = list.isEmpty() ? null : list.get(list.size() - 1);
				if (last != null && last.openTime() == periodStart) {
					BigDecimal high = price.max(last.high());
					BigDecimal low = price.min(last.low());
					list.set(list.size() - 1, new Candle(periodStart, last.open(), high, low, price));
				} else if (last == null || periodStart > last.openTime()) {
					BigDecimal open = last != null ? last.close() : price;
					list.add(new Candle(periodStart, open, open.max(price), open.min(price), price));
					trim(list);
				}
			}
			version(key).incrementAndGet();
		}
	}

	private static void trim(List<Candle> list) {
		while (list.size() > MAX_CANDLES) {
			list.remove(0);
		}
	}

	/** Wipes all cached market data (called when the player disconnects). */
	public static void invalidate() {
		CANDLES.clear();
		LOADING.clear();
		LAST_FETCH_MS.clear();
		VERSION.clear();
	}
}
