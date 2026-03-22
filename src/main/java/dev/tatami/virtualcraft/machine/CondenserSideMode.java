package dev.tatami.virtualcraft.machine;

import net.minecraft.network.chat.Component;

public enum CondenserSideMode {
	NONE(false, false, "X", "none"),
	INPUT(true, false, "I", "input"),
	OUTPUT(false, true, "O", "output"),
	BOTH(true, true, "IO", "both");

	private final boolean allowsInsert;
	private final boolean allowsExtract;
	private final String shortLabel;
	private final String translationKey;

	CondenserSideMode(boolean allowsInsert, boolean allowsExtract, String shortLabel, String translationKey) {
		this.allowsInsert = allowsInsert;
		this.allowsExtract = allowsExtract;
		this.shortLabel = shortLabel;
		this.translationKey = translationKey;
	}

	public boolean allowsInsert() {
		return allowsInsert;
	}

	public boolean allowsExtract() {
		return allowsExtract;
	}

	public String shortLabel() {
		return shortLabel;
	}

	public Component displayName() {
		return Component.translatable("tooltip.virtual_craft.side_mode." + translationKey);
	}

	public CondenserSideMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}

