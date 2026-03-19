package dev.tatami.virtualworld.machine;

public enum CondenserSideMode {
	NONE(false, false, "X"),
	INPUT(true, false, "I"),
	OUTPUT(false, true, "O"),
	BOTH(true, true, "IO");

	private final boolean allowsInsert;
	private final boolean allowsExtract;
	private final String shortLabel;

	CondenserSideMode(boolean allowsInsert, boolean allowsExtract, String shortLabel) {
		this.allowsInsert = allowsInsert;
		this.allowsExtract = allowsExtract;
		this.shortLabel = shortLabel;
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

	public CondenserSideMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
