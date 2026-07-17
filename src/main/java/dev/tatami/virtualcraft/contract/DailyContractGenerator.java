package dev.tatami.virtualcraft.contract;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class DailyContractGenerator {
	private static final int CONTRACTS_PER_DAY = 3;
	private static final long USDT_MINOR = 100L;
	private static final String MOD_ID = "virtual_craft";

	private DailyContractGenerator() {
	}

	public static List<DailyContract> generate(UUID playerId, long dayStartEpochSeconds) {
		Random random = new Random(playerId.getMostSignificantBits() ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17) ^ dayStartEpochSeconds);
		List<DailyContractType> weightedTypes = new ArrayList<>(List.of(
				DailyContractType.SETTLE_VOUCHERS,
				DailyContractType.SHOP_TRADE,
				DailyContractType.SHOP_TRADE,
				DailyContractType.SURVEY_VEIN,
				DailyContractType.SURVEY_VEIN,
				DailyContractType.SETTLE_VOUCHERS,
				DailyContractType.CONVERT_CURRENCY,
				DailyContractType.CONVERT_CURRENCY));
		EnumSet<DailyContractType> picked = EnumSet.noneOf(DailyContractType.class);
		List<DailyContract> contracts = new ArrayList<>(CONTRACTS_PER_DAY);
		while (contracts.size() < CONTRACTS_PER_DAY && !weightedTypes.isEmpty()) {
			DailyContractType type = weightedTypes.remove(random.nextInt(weightedTypes.size()));
			if (picked.add(type)) {
				contracts.add(createContract(type, contracts.size(), dayStartEpochSeconds, random));
			}
		}
		for (DailyContractType type : DailyContractType.values()) {
			if (contracts.size() >= CONTRACTS_PER_DAY) {
				break;
			}
			if (picked.add(type)) {
				contracts.add(createContract(type, contracts.size(), dayStartEpochSeconds, random));
			}
		}
		return List.copyOf(contracts);
	}

	private static DailyContract createContract(DailyContractType type, int slot, long dayStartEpochSeconds, Random random) {
		long target = switch (type) {
			case SETTLE_VOUCHERS -> 3L + random.nextInt(4);
			case SHOP_TRADE -> USDT_MINOR * (100L + random.nextInt(151));
			case SURVEY_VEIN -> 2L + random.nextInt(3);
			case CONVERT_CURRENCY -> USDT_MINOR * (50L + random.nextInt(101));
		};
		DailyContractReward reward = rewardFor(type, slot, random);
		return new DailyContract(
				type.id() + "_" + slot + "_" + dayStartEpochSeconds,
				slot,
				type,
				target,
				targetText(type, target),
				reward,
				rewardText(reward),
				iconFor(type));
	}

	private static DailyContractReward rewardFor(DailyContractType type, int slot, Random random) {
		if (type == DailyContractType.SURVEY_VEIN) {
			return new DailyContractReward(DailyContractReward.Kind.EXPLORER_HASH_VOUCHER, 1L);
		}
		if (slot == 2 && random.nextInt(100) < 12) {
			return new DailyContractReward(DailyContractReward.Kind.QUANTUM_HASH_VOUCHER, 1L);
		}
		return switch (type) {
			case SETTLE_VOUCHERS -> new DailyContractReward(DailyContractReward.Kind.BTC, 900L);
			case SHOP_TRADE -> new DailyContractReward(DailyContractReward.Kind.USDT, USDT_MINOR * (45L + slot * 15L));
			case CONVERT_CURRENCY -> new DailyContractReward(DailyContractReward.Kind.EXPLORER_HASH_VOUCHER, 1L);
			default -> new DailyContractReward(DailyContractReward.Kind.USDT, USDT_MINOR * 35L);
		};
	}

	private static String targetText(DailyContractType type, long target) {
		return switch (type) {
			case SETTLE_VOUCHERS, SURVEY_VEIN -> Long.toString(target);
			case SHOP_TRADE, CONVERT_CURRENCY -> formatUsdt(target) + " USDT";
		};
	}

	private static String rewardText(DailyContractReward reward) {
		return switch (reward.kind()) {
			case USDT -> formatUsdt(reward.amount()) + " USDT";
			case BTC -> formatBtc(reward.amount()) + " BTC";
			case EXPLORER_HASH_VOUCHER, ENCRYPTED_HASH_VOUCHER, QUANTUM_HASH_VOUCHER -> reward.amount() + "x";
		};
	}

	private static String iconFor(DailyContractType type) {
		return switch (type) {
			case SETTLE_VOUCHERS -> MOD_ID + ":hash_voucher";
			case SHOP_TRADE -> "minecraft:emerald";
			case SURVEY_VEIN -> MOD_ID + ":mining_surveyor";
			case CONVERT_CURRENCY -> MOD_ID + ":crypto_usdt";
		};
	}

	private static String formatUsdt(long minor) {
		return java.math.BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString();
	}

	private static String formatBtc(long minor) {
		return java.math.BigDecimal.valueOf(minor, 6).stripTrailingZeros().toPlainString();
	}
}
