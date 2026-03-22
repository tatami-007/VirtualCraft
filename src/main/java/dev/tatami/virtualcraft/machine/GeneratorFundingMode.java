package dev.tatami.virtualcraft.machine;

import net.minecraft.network.chat.Component;

public enum GeneratorFundingMode {
	ITEM_ONLY("item_only"),
	WALLET_ONLY("wallet_only"),
	HYBRID("hybrid");

	private final String translationKey;

	GeneratorFundingMode(String translationKey) {
		this.translationKey = translationKey;
	}

	public GeneratorFundingMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public Component displayName() {
		return Component.translatable("tooltip.virtual_craft.generator.mode." + translationKey);
	}
}
