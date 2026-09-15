package dev.tatami.virtualcraft.boss;

public final class SnakeKingAttackRules {
	public static final int SPECIAL_SIGNAL_START = 1;
	public static final int SPECIAL_ANIMATION_START = 10;
	public static final int MID_PARRY_WINDOW_TICKS = 10;
	public static final int HIGH_POISON_WARNING_START = SPECIAL_ANIMATION_START;
	public static final int HIGH_POISON_HIT_TICK = 32;
	public static final int GROUND_POISON_WARNING_START = SPECIAL_ANIMATION_START;
	public static final int GROUND_POISON_HIT_TICK = 34;
	public static final int CROWN_CHAIN_WARNING_START = 8;
	public static final int CROWN_CHAIN_HIT_TICK = 31;
	public static final int OVERHEAD_WARNING_START = SPECIAL_ANIMATION_START;
	public static final int OVERHEAD_HIT_TICK = 34;
	public static final int COIL_WARNING_START = 12;
	public static final int COIL_RELEASE_START = 48;
	public static final int COIL_RELEASE_END = 56;
	public static final double COIL_MIN_RADIUS = 1.0;
	public static final double COIL_MAX_RADIUS = 7.0;

	private SnakeKingAttackRules() {
	}

	public static BossAttackDirection directionFor(int attackId) {
		return switch (attackId) {
			case 4, 6, 10 -> BossAttackDirection.HIGH;
			case 5, 7, 8, 9 -> BossAttackDirection.LOW;
			default -> BossAttackDirection.MID;
		};
	}

	public static double coilRadius(int attackTick) {
		double progress = Math.max(0.0, Math.min(1.0,
				(attackTick - COIL_RELEASE_START)
						/ (double)(COIL_RELEASE_END - COIL_RELEASE_START)));
		double eased = progress * progress * (3.0 - 2.0 * progress);
		return COIL_MIN_RADIUS + (COIL_MAX_RADIUS - COIL_MIN_RADIUS) * eased;
	}
}
