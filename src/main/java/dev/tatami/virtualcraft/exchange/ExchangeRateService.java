package dev.tatami.virtualcraft.exchange;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.neoforged.fml.loading.FMLPaths;

public final class ExchangeRateService {
	private static final int REFRESH_INTERVAL_TICKS = 12_000;
	private static final long STALE_AFTER_SECONDS = 1_800L;
	private static final long MANUAL_REFRESH_COOLDOWN_SECONDS = 30L;
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get().resolve("virtual_craft").resolve("exchange_rates.json");
	private static volatile ExchangeRateSnapshot currentSnapshot = ExchangeRateSnapshot.defaultSnapshot();
	private static volatile boolean refreshInFlight;
	private static volatile int nextRefreshTick;
	private static volatile long lastManualRefreshEpochSeconds;

	private ExchangeRateService() {
	}

	public static ExchangeRateSnapshot getCurrentSnapshot() {
		return withStaleStatus(currentSnapshot);
	}

	public static void initialize(MinecraftServer server) {
		ExchangeRateSnapshot snapshot = loadCache().orElseGet(ExchangeRateSnapshot::defaultSnapshot);
		currentSnapshot = withStaleStatus(snapshot);
		nextRefreshTick = server.getTickCount() + REFRESH_INTERVAL_TICKS;
		refreshLiveRatesAsync(server, true);
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() >= nextRefreshTick) {
			nextRefreshTick = server.getTickCount() + REFRESH_INTERVAL_TICKS;
			refreshLiveRatesAsync(server, false);
		}
	}

	public static boolean canManualRefresh() {
		return Instant.now().getEpochSecond() - lastManualRefreshEpochSeconds >= MANUAL_REFRESH_COOLDOWN_SECONDS;
	}

	public static ExchangeRateSnapshot refreshNow(MinecraftServer server) {
		lastManualRefreshEpochSeconds = Instant.now().getEpochSecond();
		return refreshLiveRates(server);
	}

	private static void refreshLiveRatesAsync(MinecraftServer server, boolean logErrors) {
		if (refreshInFlight) {
			return;
		}
		refreshInFlight = true;
		CompletableFuture.runAsync(() -> {
			try {
				ExchangeRateSnapshot liveSnapshot = fetchLiveSnapshot();
				currentSnapshot = withStaleStatus(liveSnapshot);
				saveCache(liveSnapshot);
				VirtualCraft.LOGGER.info("Loaded live crypto exchange rates for {}", server.getMotd());
			} catch (Exception exception) {
				if (logErrors) {
					VirtualCraft.LOGGER.warn("Falling back to cached/default exchange rates", exception);
				}
			} finally {
				refreshInFlight = false;
			}
		});
	}

	private static ExchangeRateSnapshot refreshLiveRates(MinecraftServer server) {
		try {
			ExchangeRateSnapshot liveSnapshot = fetchLiveSnapshot();
			currentSnapshot = withStaleStatus(liveSnapshot);
			saveCache(liveSnapshot);
			VirtualCraft.LOGGER.info("Loaded live crypto exchange rates for {}", server.getMotd());
		} catch (Exception exception) {
			VirtualCraft.LOGGER.warn("Falling back to cached/default exchange rates", exception);
		}
		return withStaleStatus(currentSnapshot);
	}

	private static java.util.Optional<ExchangeRateSnapshot> loadCache() {
		if (!Files.exists(CACHE_PATH)) {
			return java.util.Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(CACHE_PATH, StandardCharsets.UTF_8)) {
			JsonObject root = GsonHelper.parse(reader);
			long updatedAt = GsonHelper.getAsLong(root, "updatedAtEpochSeconds", Instant.now().getEpochSecond());
			String statusName = GsonHelper.getAsString(root, "status", ExchangeRateSnapshot.SourceStatus.CACHED.name());
			JsonObject quotesJson = GsonHelper.getAsJsonObject(root, "quotes", new JsonObject());
			EnumMap<CryptoCurrency, BigDecimal> quotes = new EnumMap<>(CryptoCurrency.class);
			for (CryptoCurrency currency : CryptoCurrency.values()) {
				String raw = GsonHelper.getAsString(quotesJson, currency.id(), currency == CryptoCurrency.USDT ? "1.00" : "0");
				quotes.put(currency, new BigDecimal(raw));
			}
			ExchangeRateSnapshot.SourceStatus status;
			try {
				status = ExchangeRateSnapshot.SourceStatus.valueOf(statusName);
			} catch (IllegalArgumentException exception) {
				status = ExchangeRateSnapshot.SourceStatus.CACHED;
			}
			ExchangeRateSnapshot.SourceStatus normalized = status == ExchangeRateSnapshot.SourceStatus.LIVE ? ExchangeRateSnapshot.SourceStatus.CACHED : status;
			return java.util.Optional.of(new ExchangeRateSnapshot(quotes, updatedAt, normalized));
		} catch (Exception exception) {
			VirtualCraft.LOGGER.warn("Failed to read exchange rate cache {}", CACHE_PATH, exception);
			return java.util.Optional.empty();
		}
	}

	private static void saveCache(ExchangeRateSnapshot snapshot) {
		try {
			Files.createDirectories(CACHE_PATH.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("updatedAtEpochSeconds", snapshot.updatedAtEpochSeconds());
			root.addProperty("status", snapshot.status().name());
			JsonObject quotes = new JsonObject();
			for (CryptoCurrency currency : CryptoCurrency.values()) {
				quotes.addProperty(currency.id(), snapshot.quoteToUsdt(currency).toPlainString());
			}
			root.add("quotes", quotes);
			try (Writer writer = Files.newBufferedWriter(CACHE_PATH, StandardCharsets.UTF_8)) {
				VirtualCraft.GSON.toJson(root, writer);
			}
		} catch (IOException exception) {
			VirtualCraft.LOGGER.warn("Failed to write exchange rate cache {}", CACHE_PATH, exception);
		}
	}

	private static ExchangeRateSnapshot fetchLiveSnapshot() throws IOException, InterruptedException {
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana,tron,dogecoin&vs_currencies=usd&include_last_updated_at=true&precision=full"))
				.timeout(Duration.ofSeconds(5))
				.GET()
				.header("Accept", "application/json");
		String demoKey = System.getenv("VIRTUAL_CRAFT_COINGECKO_API_KEY");
		if (demoKey != null && !demoKey.isBlank()) {
			requestBuilder.header("x-cg-demo-api-key", demoKey.trim());
		}

		HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Unexpected CoinGecko response status: " + response.statusCode());
		}

		JsonObject root = GsonHelper.parse(response.body());
		EnumMap<CryptoCurrency, BigDecimal> quotes = new EnumMap<>(CryptoCurrency.class);
		quotes.put(CryptoCurrency.BTC, getQuote(root, "bitcoin"));
		quotes.put(CryptoCurrency.ETH, getQuote(root, "ethereum"));
		quotes.put(CryptoCurrency.USDT, BigDecimal.ONE.setScale(2, RoundingMode.DOWN));
		quotes.put(CryptoCurrency.SOL, getQuote(root, "solana"));
		quotes.put(CryptoCurrency.TRX, getQuote(root, "tron"));
		quotes.put(CryptoCurrency.DOGE, getQuote(root, "dogecoin"));
		long updatedAt = latestUpdatedAt(root);
		return new ExchangeRateSnapshot(quotes, updatedAt, ExchangeRateSnapshot.SourceStatus.LIVE);
	}

	private static BigDecimal getQuote(JsonObject root, String key) throws IOException {
		JsonObject entry = GsonHelper.getAsJsonObject(root, key);
		JsonElement valueElement = entry.get("usd");
		if (valueElement == null) {
			throw new IOException("Missing CoinGecko simple price for " + key);
		}
		return valueElement.getAsBigDecimal();
	}

	private static long latestUpdatedAt(JsonObject root) {
		long latest = Instant.now().getEpochSecond();
		for (String key : new String[] {"bitcoin", "ethereum", "solana", "tron", "dogecoin"}) {
			JsonObject entry = GsonHelper.getAsJsonObject(root, key);
			if (entry.has("last_updated_at")) {
				latest = Math.max(latest, entry.get("last_updated_at").getAsLong());
			}
		}
		return latest;
	}

	private static ExchangeRateSnapshot withStaleStatus(ExchangeRateSnapshot snapshot) {
		if (Instant.now().getEpochSecond() - snapshot.updatedAtEpochSeconds() > STALE_AFTER_SECONDS) {
			return snapshot.withStatus(ExchangeRateSnapshot.SourceStatus.STALE);
		}
		return snapshot.status() == ExchangeRateSnapshot.SourceStatus.STALE
				? new ExchangeRateSnapshot(snapshot.quotes(), snapshot.updatedAtEpochSeconds(), ExchangeRateSnapshot.SourceStatus.CACHED)
				: snapshot;
	}
}
