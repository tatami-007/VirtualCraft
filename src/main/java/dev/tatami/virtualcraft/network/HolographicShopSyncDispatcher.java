package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.exchange.ExchangeRateService;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.shop.ShopCatalogLoader;
import dev.tatami.virtualcraft.shop.ShopOffer;
import dev.tatami.virtualcraft.wallet.WalletService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Pushes the full snapshot the Holographic Terminal needs (wallet balances,
 * exchange rates, persisted preferences and the shop catalog) immediately
 * before the open packet is queued. Doing it server-side and unsolicited
 * removes the client → server REQUEST_SYNC round-trip that used to be the
 * dominant source of "blank window for ~RTT" delay when opening the terminal.
 *
 * <p>Call this after {@link HolographicTerminalSession#open} but before sending
 * {@code OpenHolographicTerminalPayload}. NeoForge / TCP preserve packet order,
 * so the client cache receives all four snapshots before the Fragment builds.</p>
 */
public final class HolographicShopSyncDispatcher {
	private HolographicShopSyncDispatcher() {
	}

	/**
	 * Pushes a full initial snapshot to the player who just opened a Holographic
	 * Terminal. Call this after {@link HolographicTerminalSession#open} and before
	 * the open payload so the first UI frame can be built from the primed cache.
	 */
	public static void pushInitialSnapshot(ServerPlayer player) {
		if (player == null) {
			return;
		}
		int menuId = HolographicTerminalSession.getOpenSessionId(player);
		if (menuId == HolographicTerminalSession.NO_SESSION) {
			return;
		}
		pushSnapshot(player, menuId, ExchangeRateService.getCurrentSnapshot());
	}

	public static void pushSnapshot(ServerPlayer player, int menuId, ExchangeRateSnapshot snapshot) {
		if (player == null || menuId == HolographicTerminalSession.NO_SESSION) {
			return;
		}
		List<ShopOffer> offers = ShopCatalogLoader.load(player.server);
		PacketDistributor.sendToPlayer(player, new SyncWalletPayload(
				menuId,
				WalletService.getWallet(player).snapshot(),
				WalletService.walletAddress(player),
				WalletService.recentEntries(player, 32).stream().map(SyncWalletPayload.Entry::fromLedgerEntry).toList()));
		PacketDistributor.sendToPlayer(player, new SyncShopCatalogPayload(
				menuId,
				offers.stream().map(SyncShopCatalogPayload.Entry::fromOffer).toList()));
		PacketDistributor.sendToPlayer(player, SyncExchangePayload.fromSnapshot(menuId, snapshot));
		PacketDistributor.sendToPlayer(player, SyncWalletPreferencesPayload.fromPreferences(menuId, player));
	}
}
