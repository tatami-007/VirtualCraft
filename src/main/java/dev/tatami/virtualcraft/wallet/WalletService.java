package dev.tatami.virtualcraft.wallet;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;

public final class WalletService {
	private WalletService() {
	}

	public static WalletBalances getWallet(Player player) {
		return player.getData(ModAttachments.WALLET_BALANCES.get());
	}

	public static WalletLedger getLedger(Player player) {
		return getWallet(player).ledger();
	}

	public static long getMinorUnits(Player player, CryptoCurrency currency) {
		return getWallet(player).getMinorUnits(currency);
	}

	public static boolean canAfford(Player player, CryptoCurrency currency, long amount) {
		return getWallet(player).canAfford(currency, amount);
	}

	public static long depositMinorUnits(Player player, CryptoCurrency currency, long amount) {
		return getWallet(player).depositMinorUnits(currency, amount);
	}

	public static long depositWholeItem(Player player, CryptoCurrency currency, long itemCount) {
		return getWallet(player).depositWholeItem(currency, itemCount);
	}

	public static boolean withdrawMinorUnits(Player player, CryptoCurrency currency, long amount) {
		return getWallet(player).withdrawMinorUnits(currency, amount);
	}

	public static boolean withdrawWholeItem(Player player, CryptoCurrency currency, long itemCount) {
		return getWallet(player).withdrawWholeItem(currency, itemCount);
	}

	public static boolean transfer(Player from, Player to, CryptoCurrency currency, long amount) {
		if (from == to || amount <= 0 || !withdrawMinorUnits(from, currency, amount)) {
			return false;
		}
		depositMinorUnits(to, currency, amount);
		return true;
	}

	public static void record(Player player, WalletLedgerType type, CryptoCurrency primaryCurrency, long primaryAmountMinor, CryptoCurrency secondaryCurrency, long secondaryAmountMinor, String detail) {
		getLedger(player).add(new WalletLedgerEntry(
				type,
				primaryCurrency == null ? "" : primaryCurrency.id(),
				Math.max(0L, primaryAmountMinor),
				secondaryCurrency == null ? "" : secondaryCurrency.id(),
				Math.max(0L, secondaryAmountMinor),
				detail == null ? "" : detail,
				Instant.now().getEpochSecond()));
	}

	public static List<WalletLedgerEntry> recentEntries(Player player, int limit) {
		return getLedger(player).recent(limit);
	}

	public static String walletAddress(Player player) {
		UUID uuid = player.getUUID();
		String normalized = uuid.toString().replace("-", "").toUpperCase(Locale.ROOT);
		return "VC-" + normalized.substring(0, 10);
	}
}
