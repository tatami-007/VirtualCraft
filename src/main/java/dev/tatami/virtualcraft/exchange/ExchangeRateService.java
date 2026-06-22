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
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.network.SyncExchangePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ExchangeRateService {
	private static final int HTTP_FALLBACK_INTERVAL_TICKS = 1200;
	private static final int BROADCAST_THROTTLE_TICKS = 20;
	private static final long STALE_AFTER_SECONDS = 120L;
	private static final long MANUAL_REFRESH_COOLDOWN_SECONDS = 5L;
	private static final long WS_RECONNECT_INTERVAL_MS = 10_000L;
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get().resolve("virtual_craft").resolve("exchange_rates.json");

	private static volatile ExchangeRateSnapshot currentSnapshot = ExchangeRateSnapshot.defaultSnapshot();
	private static volatile boolean refreshInFlight;
	private static volatile int nextHttpRefreshTick;
	private static volatile long lastManualRefreshEpochSeconds;
	private static volatile boolean lastRefreshFailed;

	private static volatile WebSocket activeWebSocket;
	private static volatile boolean wsConnected;
	private static volatile long lastWsReconnectAttemptMs;
	private static final EnumMap<CryptoCurrency, BigDecimal> livePrices = new EnumMap<>(CryptoCurrency.class);
	private static volatile ExchangeRateSnapshot pendingBroadcast;
	private static volatile long lastBroadcastTick;
	private static volatile MinecraftServer activeServer;

	private ExchangeRateService() {
	}

	public static ExchangeRateSnapshot getCurrentSnapshot() {
		return withStaleStatus(currentSnapshot);
	}

	public static void initialize(MinecraftServer server) {
		activeServer = server;
		ExchangeRateSnapshot snapshot = loadCache().orElseGet(ExchangeRateSnapshot::defaultSnapshot);
		currentSnapshot = withStaleStatus(snapshot);
		for (CryptoCurrency c : CryptoCurrency.values()) {
			livePrices.put(c, snapshot.quoteToUsdt(c));
		}
		nextHttpRefreshTick = server.getTickCount() + HTTP_FALLBACK_INTERVAL_TICKS;
		refreshLiveRatesAsync(server, true);
		connectWebSocket();
	}

	public static void tick(MinecraftServer server) {
		activeServer = server;

		if (!wsConnected && System.currentTimeMillis() - lastWsReconnectAttemptMs > WS_RECONNECT_INTERVAL_MS) {
			connectWebSocket();
		}

		if (pendingBroadcast != null && server.getTickCount() - lastBroadcastTick >= BROADCAST_THROTTLE_TICKS) {
			ExchangeRateSnapshot toSend = pendingBroadcast;
			pendingBroadcast = null;
			lastBroadcastTick = server.getTickCount();
			broadcastToOpenTerminals(server, toSend);
		}

		if (!wsConnected && server.getTickCount() >= nextHttpRefreshTick) {
			nextHttpRefreshTick = server.getTickCount() + HTTP_FALLBACK_INTERVAL_TICKS;
			refreshLiveRatesAsync(server, false);
		}
	}

	public static boolean canManualRefresh() {
		return Instant.now().getEpochSecond() - lastManualRefreshEpochSeconds >= MANUAL_REFRESH_COOLDOWN_SECONDS;
	}

	public static boolean didLastRefreshFail() {
		return lastRefreshFailed;
	}

	public static boolean isWebSocketConnected() {
		return wsConnected;
	}

	public static ExchangeRateSnapshot refreshNow(MinecraftServer server) {
		lastManualRefreshEpochSeconds = Instant.now().getEpochSecond();
		if (!wsConnected) {
			connectWebSocket();
		}
		return refreshLiveRates(server);
	}

	public static void shutdown() {
		WebSocket ws = activeWebSocket;
		if (ws != null) {
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "Server shutting down");
			} catch (Exception ignored) {
			}
			activeWebSocket = null;
		}
		wsConnected = false;
		activeServer = null;
		pendingBroadcast = null;
	}

	// ---- WebSocket ----

	private static void connectWebSocket() {
		lastWsReconnectAttemptMs = System.currentTimeMillis();
		try {
			HttpClient.newHttpClient()
					.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.buildAsync(
							URI.create("wss://ws.coincap.io/prices?assets=bitcoin,ethereum,solana,dogecoin,tron"),
							new CoinCapListener())
					.thenAccept(ws -> {
						activeWebSocket = ws;
						wsConnected = true;
						lastRefreshFailed = false;
						VirtualCraft.LOGGER.info("Connected to CoinCap WebSocket for real-time prices");
					})
					.exceptionally(ex -> {
						wsConnected = false;
						VirtualCraft.LOGGER.debug("CoinCap WebSocket connect failed", ex);
						return null;
					});
		} catch (Exception e) {
			VirtualCraft.LOGGER.debug("Failed to initiate CoinCap WebSocket", e);
		}
	}

	private static CryptoCurrency mapCoinCapId(String id) {
		return switch (id) {
			case "bitcoin" -> CryptoCurrency.BTC;
			case "ethereum" -> CryptoCurrency.ETH;
			case "solana" -> CryptoCurrency.SOL;
			case "tron" -> CryptoCurrency.TRX;
			case "dogecoin" -> CryptoCurrency.DOGE;
			default -> null;
		};
	}

	private static final class CoinCapListener implements WebSocket.Listener {
		private final StringBuilder buffer = new StringBuilder();

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			buffer.append(data);
			if (!last) {
				webSocket.request(1);
				return CompletableFuture.completedFuture(null);
			}
			String message = buffer.toString();
			buffer.setLength(0);
			try {
				JsonObject obj = GsonHelper.parse(message);
				boolean changed = false;
				synchronized (livePrices) {
					for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
						CryptoCurrency currency = mapCoinCapId(entry.getKey());
						if (currency != null) {
							BigDecimal price = new BigDecimal(entry.getValue().getAsString());
							BigDecimal old = livePrices.get(currency);
							if (old == null || old.compareTo(price) != 0) {
								livePrices.put(currency, price);
								changed = true;
							}
						}
					}
				}
				if (changed) {
					EnumMap<CryptoCurrency, BigDecimal> copy;
					synchronized (livePrices) {
						copy = new EnumMap<>(livePrices);
					}
					ExchangeRateSnapshot fresh = new ExchangeRateSnapshot(
							copy, Instant.now().getEpochSecond(), ExchangeRateSnapshot.SourceStatus.LIVE);
					currentSnapshot = fresh;
					pendingBroadcast = fresh;
					lastRefreshFailed = false;
					saveCache(fresh);
				}
			} catch (Exception e) {
				VirtualCraft.LOGGER.debug("Failed to parse CoinCap WS message: {}", message, e);
			}
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			wsConnected = false;
			activeWebSocket = null;
			VirtualCraft.LOGGER.info("CoinCap WebSocket closed: {} {}", statusCode, reason);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			wsConnected = false;
			activeWebSocket = null;
			VirtualCraft.LOGGER.debug("CoinCap WebSocket error", error);
		}
	}

	// ---- Broadcast ----

	private static void broadcastToOpenTerminals(MinecraftServer server, ExchangeRateSnapshot snapshot) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int sessionId = HolographicTerminalSession.getOpenSessionId(player);
			if (sessionId != HolographicTerminalSession.NO_SESSION) {
				PacketDistributor.sendToPlayer(player, SyncExchangePayload.fromSnapshot(sessionId, snapshot));
			}
		}
	}

	// ---- HTTP fallback ----

	private static void refreshLiveRatesAsync(MinecraftServer server, boolean logErrors) {
		if (refreshInFlight) {
			return;
		}
		refreshInFlight = true;
		CompletableFuture.runAsync(() -> {
			try {
				ExchangeRateSnapshot liveSnapshot = fetchLiveSnapshot();
				currentSnapshot = withStaleStatus(liveSnapshot);
				lastRefreshFailed = false;
				saveCache(liveSnapshot);
				synchronized (livePrices) {
					for (CryptoCurrency c : CryptoCurrency.values()) {
						livePrices.put(c, liveSnapshot.quoteToUsdt(c));
					}
				}
				VirtualCraft.LOGGER.info("Loaded live crypto exchange rates via HTTP");
			} catch (Exception exception) {
				lastRefreshFailed = true;
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
			lastRefreshFailed = false;
			saveCache(liveSnapshot);
			synchronized (livePrices) {
				for (CryptoCurrency c : CryptoCurrency.values()) {
					livePrices.put(c, liveSnapshot.quoteToUsdt(c));
				}
			}
			VirtualCraft.LOGGER.info("Loaded live crypto exchange rates via HTTP (manual)");
		} catch (Exception exception) {
			lastRefreshFailed = true;
			VirtualCraft.LOGGER.warn("Falling back to cached/default exchange rates", exception);
		}
		return withStaleStatus(currentSnapshot);
	}

	private static ExchangeRateSnapshot fetchLiveSnapshot() throws IOException, InterruptedException {
		try {
			return fetchFromCoinGecko();
		} catch (Exception primaryEx) {
			VirtualCraft.LOGGER.debug("CoinGecko unavailable, trying CoinCap REST fallback", primaryEx);
			try {
				return fetchFromCoinCapRest();
			} catch (Exception fallbackEx) {
				primaryEx.addSuppressed(fallbackEx);
				throw primaryEx;
			}
		}
	}

	private static ExchangeRateSnapshot fetchFromCoinGecko() throws IOException, InterruptedException {
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana,tron,dogecoin&vs_currencies=usd&include_last_updated_at=true&precision=full"))
				.timeout(Duration.ofSeconds(10))
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

	private static ExchangeRateSnapshot fetchFromCoinCapRest() throws IOException, InterruptedException {
		String[] ids = {"bitcoin", "ethereum", "solana", "tron", "dogecoin"};
		EnumMap<CryptoCurrency, BigDecimal> quotes = new EnumMap<>(CryptoCurrency.class);
		quotes.put(CryptoCurrency.USDT, BigDecimal.ONE.setScale(2, RoundingMode.DOWN));
		for (String id : ids) {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://api.coincap.io/v2/assets/" + id))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.header("Accept", "application/json")
					.build();
			HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException("CoinCap REST error for " + id + ": " + response.statusCode());
			}
			JsonObject root = GsonHelper.parse(response.body());
			JsonObject data = GsonHelper.getAsJsonObject(root, "data");
			BigDecimal price = new BigDecimal(GsonHelper.getAsString(data, "priceUsd", "0"));
			CryptoCurrency currency = mapCoinCapId(id);
			if (currency != null) {
				quotes.put(currency, price);
			}
		}
		return new ExchangeRateSnapshot(quotes, Instant.now().getEpochSecond(), ExchangeRateSnapshot.SourceStatus.LIVE);
	}

	// ---- Cache ----

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

	// ---- Helpers ----

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
