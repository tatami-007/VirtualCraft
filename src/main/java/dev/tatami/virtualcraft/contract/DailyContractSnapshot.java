package dev.tatami.virtualcraft.contract;

public record DailyContractSnapshot(
		String id,
		String typeId,
		String titleKey,
		String goalKey,
		String targetText,
		long target,
		long progress,
		boolean claimed,
		String rewardKind,
		String rewardText,
		String iconItemId) {
}
