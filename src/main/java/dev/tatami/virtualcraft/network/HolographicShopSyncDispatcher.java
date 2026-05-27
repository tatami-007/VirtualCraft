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
 * after the open packet has been queued. Doing it server-side and unsolicited
 * removes the client → server REQUEST_SYNC round-trip that used to be the
 * dominant source of "blank window for ~RTT" delay when opening the terminal.
 *
 * <p>Call this <strong>after</strong> {@link HolographicTerminalSession#open}
 * has registered the player and {@code OpenHolographicTerminalPayload} has
 * been sent on the same connection. NeoForge / TCP keep packet order, so the
 * client receives Open first (which constructs the Fragment with the right
 * sessionId) and then the four sync packets arriving here, which the Fragment
 * matches via {@code fromCurrentScreen(menuId)}.</p>
 */
public final class HolographicShopSyncDispatcher {
	private HolographicShopSyncDispatcher() {
	}

	/**
	 * Pushes a full initial snapshot to the player who just opened a Holographic
	 * Terminal. Call this <strong>after</strong> {@link HolographicTerminalSession#open}
	 * has been called and the {@code OpenHolographicTerminalPayload} has been
	 * queued; the sync packets pushed here will arrive at the client right after
	 * the Open packet so the client-side Fragment exists by the time they hit.
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
