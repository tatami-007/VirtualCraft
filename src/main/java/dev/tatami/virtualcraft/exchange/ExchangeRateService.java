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

	private static final String BINANCE_REST = "https://data-api.binance.vision/api/v3/ticker/price";
	private static final String BINANCE_WS = "wss://data-stream.binance.vision/ws/"
			+ "btcusdt@miniTicker/ethusdt@miniTicker/solusdt@miniTicker/dogeusdt@miniTicker/trxusdt@miniTicker";

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

	private ExchangeRateService() {
	}

	public static ExchangeRateSnapshot getCurrentSnapshot() {
		return withStaleStatus(currentSnapshot);
	}

	public static void initialize(MinecraftServer server) {
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
		pendingBroadcast = null;
	}

	// ---- WebSocket (Binance Vision) ----

	private static void connectWebSocket() {
		lastWsReconnectAttemptMs = System.currentTimeMillis();
		try {
			HttpClient.newHttpClient()
					.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.buildAsync(URI.create(BINANCE_WS), new BinanceWsListener())
					.thenAccept(ws -> {
						activeWebSocket = ws;
						wsConnected = true;
						lastRefreshFailed = false;
						VirtualCraft.LOGGER.info("Connected to Binance WebSocket for real-time prices");
					})
					.exceptionally(ex -> {
						wsConnected = false;
						VirtualCraft.LOGGER.debug("Binance WebSocket connect failed", ex);
						return null;
					});
		} catch (Exception e) {
			VirtualCraft.LOGGER.debug("Failed to initiate Binance WebSocket", e);
		}
	}

	private static CryptoCurrency mapBinanceSymbol(String symbol) {
		return switch (symbol) {
			case "BTCUSDT" -> CryptoCurrency.BTC;
			case "ETHUSDT" -> CryptoCurrency.ETH;
			case "SOLUSDT" -> CryptoCurrency.SOL;
			case "TRXUSDT" -> CryptoCurrency.TRX;
			case "DOGEUSDT" -> CryptoCurrency.DOGE;
			default -> null;
		};
	}

	private static final class BinanceWsListener implements WebSocket.Listener {
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
				String symbol = GsonHelper.getAsString(obj, "s", "");
				String closePrice = GsonHelper.getAsString(obj, "c", "");
				if (!symbol.isEmpty() && !closePrice.isEmpty()) {
					CryptoCurrency currency = mapBinanceSymbol(symbol);
					if (currency != null) {
						BigDecimal price = new BigDecimal(closePrice);
						boolean changed;
						synchronized (livePrices) {
							BigDecimal old = livePrices.get(currency);
							changed = old == null || old.compareTo(price) != 0;
							if (changed) {
								livePrices.put(currency, price);
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
						}
					}
				}
			} catch (Exception e) {
				VirtualCraft.LOGGER.debug("Failed to parse Binance WS message: {}", message, e);
			}
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			wsConnected = false;
			activeWebSocket = null;
			VirtualCraft.LOGGER.info("Binance WebSocket closed: {} {}", statusCode, reason);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			wsConnected = false;
			activeWebSocket = null;
			VirtualCraft.LOGGER.debug("Binance WebSocket error", error);
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

	// ---- HTTP fallback (Binance Vision REST) ----

	private static void refreshLiveRatesAsync(MinecraftServer server, boolean logErrors) {
		if (refreshInFlight) {
			return;
		}
		refreshInFlight = true;
		CompletableFuture.runAsync(() -> {
			try {
				ExchangeRateSnapshot liveSnapshot = fetchFromBinanceRest();
				currentSnapshot = withStaleStatus(liveSnapshot);
				lastRefreshFailed = false;
				saveCache(liveSnapshot);
				synchronized (livePrices) {
					for (CryptoCurrency c : CryptoCurrency.values()) {
						livePrices.put(c, liveSnapshot.quoteToUsdt(c));
					}
				}
				pendingBroadcast = withStaleStatus(liveSnapshot);
				VirtualCraft.LOGGER.info("Loaded live crypto exchange rates via Binance REST");
			} catch (Exception exception) {
				lastRefreshFailed = true;
				if (logErrors) {
					VirtualCraft.LOGGER.warn("Failed to fetch exchange rates", exception);
				}
			} finally {
				refreshInFlight = false;
			}
		});
	}

	private static ExchangeRateSnapshot refreshLiveRates(MinecraftServer server) {
		try {
			ExchangeRateSnapshot liveSnapshot = fetchFromBinanceRest();
			currentSnapshot = withStaleStatus(liveSnapshot);
			lastRefreshFailed = false;
			saveCache(liveSnapshot);
			synchronized (livePrices) {
				for (CryptoCurrency c : CryptoCurrency.values()) {
					livePrices.put(c, liveSnapshot.quoteToUsdt(c));
				}
			}
			VirtualCraft.LOGGER.info("Loaded live crypto exchange rates via Binance REST (manual)");
		} catch (Exception exception) {
			lastRefreshFailed = true;
			VirtualCraft.LOGGER.warn("Failed to fetch exchange rates", exception);
		}
		return withStaleStatus(currentSnapshot);
	}

	private static ExchangeRateSnapshot fetchFromBinanceRest() throws IOException, InterruptedException {
		String[][] pairs = {
				{"BTCUSDT", "BTC"}, {"ETHUSDT", "ETH"}, {"SOLUSDT", "SOL"},
				{"TRXUSDT", "TRX"}, {"DOGEUSDT", "DOGE"}
		};
		EnumMap<CryptoCurrency, BigDecimal> quotes = new EnumMap<>(CryptoCurrency.class);
		quotes.put(CryptoCurrency.USDT, BigDecimal.ONE.setScale(2, RoundingMode.DOWN));

		for (String[] pair : pairs) {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(BINANCE_REST + "?symbol=" + pair[0]))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.header("Accept", "application/json")
					.build();
			HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException("Binance REST error for " + pair[0] + ": " + response.statusCode());
			}
			JsonObject obj = GsonHelper.parse(response.body());
			String priceStr = GsonHelper.getAsString(obj, "price", "0");
			BigDecimal price = new BigDecimal(priceStr);
			CryptoCurrency currency = CryptoCurrency.byId(pair[1].toLowerCase()).orElse(null);
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

	private static ExchangeRateSnapshot withStaleStatus(ExchangeRateSnapshot snapshot) {
		if (Instant.now().getEpochSecond() - snapshot.updatedAtEpochSeconds() > STALE_AFTER_SECONDS) {
			return snapshot.withStatus(ExchangeRateSnapshot.SourceStatus.STALE);
		}
		return snapshot.status() == ExchangeRateSnapshot.SourceStatus.STALE
				? new ExchangeRateSnapshot(snapshot.quotes(), snapshot.updatedAtEpochSeconds(), ExchangeRateSnapshot.SourceStatus.CACHED)
				: snapshot;
	}
}
