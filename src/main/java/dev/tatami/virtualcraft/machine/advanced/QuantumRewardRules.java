package dev.tatami.virtualcraft.machine.advanced;

public final class QuantumRewardRules {
	private QuantumRewardRules() {
	}

	public static int stabilityPermille(long runningTicks, long waitingForPowerTicks) {
		long total = Math.max(1L, runningTicks + waitingForPowerTicks);
		return (int) Math.clamp(runningTicks * 1000L / total, 0L, 1000L);
	}

	public static int materialYield(int memoryCount, int stabilityPermille) {
		return Math.clamp(4 + memoryCount / 2 + stabilityPermille / 250, 4, 16);
	}

	public static int componentYield(int memoryCount, int stabilityPermille) {
		int stableBonus = stabilityPermille >= 950 ? 1 : 0;
		return Math.clamp(1 + memoryCount / 6 + stableBonus, 1, 4);
	}

	public static VoucherTier hashTier(int cpuCount, int gpuCount, int memoryCount, int stabilityPermille) {
		int score = cpuCount + gpuCount * 2 + memoryCount / 2 + stabilityPermille / 200;
		if (score >= 34) {
			return VoucherTier.GENESIS;
		}
		if (score >= 23) {
			return VoucherTier.QUANTUM;
		}
		if (score >= 16) {
			return VoucherTier.ENCRYPTED;
		}
		if (score >= 10) {
			return VoucherTier.EXPLORER;
		}
		return VoucherTier.BASIC;
	}

	public static int hashYield(int gpuCount, int stabilityPermille, VoucherTier tier) {
		if (tier == VoucherTier.GENESIS) {
			return 1;
		}
		int stableBonus = stabilityPermille >= 900 ? 1 : 0;
		return Math.clamp(1 + gpuCount / 6 + stableBonus, 1, 3);
	}

	public enum VoucherTier {
		BASIC,
		EXPLORER,
		ENCRYPTED,
		QUANTUM,
		GENESIS
	}
}
