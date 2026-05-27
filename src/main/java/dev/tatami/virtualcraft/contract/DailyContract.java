package dev.tatami.virtualcraft.contract;

public record DailyContract(
		String id,
		int slot,
		DailyContractType type,
		long target,
		String targetText,
		DailyContractReward reward,
		String rewardText,
		String iconItemId) {
	public String titleKey() {
		return "screen.virtual_craft.holographic_shop.contracts." + type.id() + ".title";
	}

	public String goalKey() {
		return "screen.virtual_craft.holographic_shop.contracts." + type.id() + ".goal";
	}
}
