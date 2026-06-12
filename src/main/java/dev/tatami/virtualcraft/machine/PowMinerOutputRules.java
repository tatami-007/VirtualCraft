package dev.tatami.virtualcraft.machine;

public final class PowMinerOutputRules {
	public static final int BASIC_OUTPUT_SLOT = 0;
	public static final int RARE_OUTPUT_SLOT = 1;
	public static final int OUTPUT_SLOTS = 2;

	private PowMinerOutputRules() {
	}

	public static int normalizedSlotCount(int savedSlotCount) {
		return Math.max(OUTPUT_SLOTS, savedSlotCount);
	}
}
