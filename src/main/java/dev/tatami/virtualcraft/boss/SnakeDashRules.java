package dev.tatami.virtualcraft.boss;

/** Timing and travel rules shared by both passes of the virtual dash. */
public final class SnakeDashRules {
	public static final int FIRST_START = 32;
	public static final int SECOND_START = 56;
	public static final int ACTIVE_TICKS = 8;
	private static final double STOP_DISTANCE = 1.35;
	private static final double MIN_SPEED = 0.55;
	private static final double MAX_SPEED = 2.15;

	private SnakeDashRules() {
	}

	public static boolean isActive(int attackTick) {
		return isPassActive(attackTick, FIRST_START) || isPassActive(attackTick, SECOND_START);
	}

	public static boolean isPassStart(int attackTick) {
		return attackTick == FIRST_START || attackTick == SECOND_START;
	}

	public static boolean isPassEnd(int attackTick) {
		return attackTick == FIRST_START + ACTIVE_TICKS || attackTick == SECOND_START + ACTIVE_TICKS;
	}

	public static double speedForDistance(double distance) {
		double requested = (Math.max(0.0, distance) - STOP_DISTANCE) / ACTIVE_TICKS;
		return Math.max(MIN_SPEED, Math.min(MAX_SPEED, requested));
	}

	private static boolean isPassActive(int attackTick, int start) {
		return attackTick >= start && attackTick < start + ACTIVE_TICKS;
	}
}
