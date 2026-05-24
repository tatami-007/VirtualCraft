package dev.tatami.virtualcraft.client.screen.holographic;

import java.util.ArrayList;
import java.util.List;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import dev.tatami.virtualcraft.network.SyncShopCatalogPayload;
import dev.tatami.virtualcraft.network.SyncWalletPayload;
import dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload;
import dev.tatami.virtualcraft.shop.ShopOffer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Process-level cache of the most recent Holographic Terminal snapshot the
 * client has seen for the current player. The Fragment uses it to render
 * meaningful content on the very first frame, before the server-pushed
 * sync packets are processed by the network thread.
 *
 * <p>Even though the server pushes a fresh snapshot right after openMenu,
 * the actual {@code Sync*Payload} packets are still parsed on the network
 * thread and dispatched to the main thread, which means the very first
 * draw of {@link HolographicShopFragment} typically happens with empty
 * defaults (zero balances, no offers, default exchange rates). Pre-filling
 * those fields from the last cached snapshot makes re-opens essentially
 * instantaneous and removes the visible "blank panel → numbers appear"
 * transition.</p>
 *
 * <p>The cache is intentionally <em>process-wide</em>, not per-screen, so
 * that closing and immediately re-opening the same terminal (or moving
 * between an item terminal and a block terminal) reuses the same data.
 * Crossing servers / disconnecting wipes the cache via {@link #invalidate()}
 * so that we never display stale balances from a previous session.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HolographicShopClientCache {
	private static final long[] EMPTY_BALANCES = new long[CryptoCurrency.values().length];

	private static volatile long[] balances = EMPTY_BALANCES.clone();
	private static volatile String walletAddress = "VC-UNKNOWN";
	private static volatile List<SyncWalletPayload.Entry> recentEntries = List.of();
	private static volatile ExchangeRateSnapshot exchangeSnapshot = ExchangeRateSnapshot.defaultSnapshot();
	private static volatile List<ShopOffer> offers = List.of();
	private static volatile SyncWalletPreferencesPayload preferences;
	private static volatile boolean hasWalletSnapshot;
	private static volatile boolean hasCatalogSnapshot;

	private HolographicShopClientCache() {
	}

	public static void storeWallet(long[] balanceArray, String address, List<SyncWalletPayload.Entry> entries) {
		long[] copy = new long[EMPTY_BALANCES.length];
		System.arraycopy(balanceArray, 0, copy, 0, Math.min(copy.length, balanceArray.length));
		balances = copy;
		walletAddress = address == null ? "VC-UNKNOWN" : address;
		recentEntries = entries == null ? List.of() : List.copyOf(entries);
		hasWalletSnapshot = true;
	}

	public static void storeExchange(ExchangeRateSnapshot snapshot) {
		if (snapshot != null) {
			exchangeSnapshot = snapshot;
		}
	}

	public static void storeCatalog(List<SyncShopCatalogPayload.Entry> entries) {
		List<ShopOffer> resolved = new ArrayList<>(entries == null ? 0 : entries.size());
		if (entries != null) {
			for (SyncShopCatalogPayload.Entry entry : entries) {
				ShopOffer offer = entry.toOffer();
				if (offer != null) {
					resolved.add(offer);
				}
			}
		}
		offers = List.copyOf(resolved);
		hasCatalogSnapshot = true;
	}

	public static void storePreferences(SyncWalletPreferencesPayload payload) {
		if (payload != null) {
			preferences = payload;
		}
	}

	public static long[] balancesCopy() {
		return balances.clone();
	}

	public static String walletAddress() {
		return walletAddress;
	}

	public static List<SyncWalletPayload.Entry> recentEntries() {
		return recentEntries;
	}

	public static ExchangeRateSnapshot exchangeSnapshot() {
		return exchangeSnapshot;
	}

	public static List<ShopOffer> offers() {
		return offers;
	}

	public static SyncWalletPreferencesPayload preferences() {
		return preferences;
	}

	/** {@code true} if the client has ever received a wallet sync packet for the current player. */
	public static boolean hasWalletSnapshot() {
		return hasWalletSnapshot;
	}

	/** {@code true} if the client has ever received a shop catalog sync packet for the current session. */
	public static boolean hasCatalogSnapshot() {
		return hasCatalogSnapshot;
	}

	/**
	 * Drops cached data when the player disconnects so we never paint stale
	 * balances or addresses from a previous session into a fresh terminal.
	 */
	public static void invalidate() {
		balances = EMPTY_BALANCES.clone();
		walletAddress = "VC-UNKNOWN";
		recentEntries = List.of();
		exchangeSnapshot = ExchangeRateSnapshot.defaultSnapshot();
		offers = List.of();
		preferences = null;
		hasWalletSnapshot = false;
		hasCatalogSnapshot = false;
	}
}
