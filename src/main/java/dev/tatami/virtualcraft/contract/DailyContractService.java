package dev.tatami.virtualcraft.contract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.wallet.WalletLedgerType;
import dev.tatami.virtualcraft.wallet.WalletPreferences;
import dev.tatami.virtualcraft.wallet.WalletService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;

public final class DailyContractService {
	private static final long DAY_SECONDS = 86_400L;

	private DailyContractService() {
	}

	public static long currentDayStartEpochSeconds() {
		long now = Instant.now().getEpochSecond();
		return now - (now % DAY_SECONDS);
	}

	public static List<DailyContractSnapshot> snapshots(ServerPlayer player) {
		return snapshots(player.getUUID(), WalletService.getPreferences(player));
	}

	public static List<DailyContractSnapshot> snapshots(UUID playerId, WalletPreferences preferences) {
		List<DailyContract> contracts = ensureContracts(playerId, preferences);
		List<DailyContractSnapshot> snapshots = new ArrayList<>(contracts.size());
		for (DailyContract contract : contracts) {
			long progress = Math.min(contract.target(), preferences.contractProgress(contract.id()));
			snapshots.add(new DailyContractSnapshot(
					contract.id(),
					contract.type().id(),
					contract.titleKey(),
					contract.goalKey(),
					contract.targetText(),
					contract.target(),
					progress,
					preferences.isContractClaimed(contract.id()),
					contract.reward().kind().name().toLowerCase(java.util.Locale.ROOT),
					contract.rewardText(),
					contract.iconItemId()));
		}
		return snapshots;
	}

	public static void recordProgress(ServerPlayer player, DailyContractType type, long amount) {
		if (amount <= 0L) {
			return;
		}
		WalletPreferences preferences = WalletService.getPreferences(player);
		for (DailyContract contract : ensureContracts(player.getUUID(), preferences)) {
			if (contract.type() == type && !preferences.isContractClaimed(contract.id())) {
				preferences.addContractProgress(contract.id(), amount, contract.target());
			}
		}
	}

	public static boolean claim(ServerPlayer player, String contractId) {
		if (contractId == null || contractId.isBlank()) {
			return false;
		}
		WalletPreferences preferences = WalletService.getPreferences(player);
		for (DailyContract contract : ensureContracts(player.getUUID(), preferences)) {
			if (!contract.id().equals(contractId)) {
				continue;
			}
			if (preferences.isContractClaimed(contract.id()) || preferences.contractProgress(contract.id()) < contract.target()) {
				return false;
			}
			if (!preferences.claimContract(contract.id())) {
				return false;
			}
			grantReward(player, contract.reward());
			WalletService.record(player, WalletLedgerType.CONTRACT_REWARD, rewardLedgerCurrency(contract.reward()), rewardLedgerAmount(contract.reward()), null, 0L, contract.type().id());
			player.displayClientMessage(Component.translatable("message.virtual_craft.contract.claimed"), true);
			return true;
		}
		return false;
	}

	private static List<DailyContract> ensureContracts(UUID playerId, WalletPreferences preferences) {
		long dayStart = currentDayStartEpochSeconds();
		if (preferences.contractDayStartEpochSeconds() != dayStart) {
			preferences.resetContracts(dayStart);
		}
		return generate(playerId, dayStart);
	}

	public static List<DailyContract> generate(UUID playerId, long dayStartEpochSeconds) {
		return DailyContractGenerator.generate(playerId, dayStartEpochSeconds);
	}

	private static CryptoCurrency rewardLedgerCurrency(DailyContractReward reward) {
		return reward.kind() == DailyContractReward.Kind.BTC ? CryptoCurrency.BTC : CryptoCurrency.USDT;
	}

	private static long rewardLedgerAmount(DailyContractReward reward) {
		return switch (reward.kind()) {
			case BTC, USDT -> reward.amount();
			default -> 0L;
		};
	}

	private static void grantReward(ServerPlayer player, DailyContractReward reward) {
		switch (reward.kind()) {
			case USDT -> WalletService.depositMinorUnits(player, CryptoCurrency.USDT, reward.amount());
			case BTC -> WalletService.depositMinorUnits(player, CryptoCurrency.BTC, reward.amount());
			case DATA_FRAGMENT -> give(player, new ItemStack(ModItems.DATA_FRAGMENT.get(), safeItemCount(reward.amount())));
			case EXPLORER_HASH_VOUCHER -> give(player, new ItemStack(ModItems.EXPLORER_HASH_VOUCHER.get(), safeItemCount(reward.amount())));
			case ENCRYPTED_HASH_VOUCHER -> give(player, new ItemStack(ModItems.ENCRYPTED_HASH_VOUCHER.get(), safeItemCount(reward.amount())));
			case QUANTUM_HASH_VOUCHER -> give(player, new ItemStack(ModItems.QUANTUM_HASH_VOUCHER.get(), safeItemCount(reward.amount())));
		}
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
	}

	private static int safeItemCount(long amount) {
		return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, amount));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
		}
	}
}
