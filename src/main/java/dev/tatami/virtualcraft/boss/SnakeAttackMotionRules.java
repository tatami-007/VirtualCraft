package dev.tatami.virtualcraft.boss;

/**
 * Keeps attack movement limited to authored impulse windows.
 *
 * <p>A navigation stop does not remove existing velocity. Without this rule a
 * bite lunge keeps sliding through its recovery animation and reads as crawling.
 */
public final class SnakeAttackMotionRules {
	private static final int ATTACK_BITE = 1;
	private static final int ATTACK_DOUBLE_HUNT = 2;
	private static final int ATTACK_VOID_DASH = 7;
	private static final int ATTACK_OVERHEAD_FANG = 10;

	private SnakeAttackMotionRules() {
	}

	public static double horizontalRetention(int attack, int attackTick, SnakeHuntPattern huntPattern) {
		return switch (attack) {
			case ATTACK_BITE -> impulseRetention(attackTick, SnakeHuntPattern.BITE_LUNGE_TICK, 3);
			case ATTACK_DOUBLE_HUNT -> comboRetention(attackTick, huntPattern);
			// The dash writes an authored velocity throughout its active window.
			// Never retain it into recovery or the second wind-up.
			case ATTACK_VOID_DASH -> 0.0;
			case ATTACK_OVERHEAD_FANG -> impulseRetention(
					attackTick, SnakeKingAttackRules.OVERHEAD_HIT_TICK - 3, 4);
			default -> 0.0;
		};
	}

	private static double comboRetention(int attackTick, SnakeHuntPattern huntPattern) {
		if (SnakeHuntPattern.isComboOpening(attackTick)) {
			return 0.0;
		}
		int segment = huntPattern.comboSegmentAt(attackTick);
		if (segment >= huntPattern.segmentCount()
				|| huntPattern.move(segment) != SnakeHuntPattern.Move.BITE) {
			return 0.0;
		}
		return impulseRetention(huntPattern.comboLocalTickAt(attackTick),
				SnakeHuntPattern.BITE_LUNGE_TICK, 3);
	}

	private static double impulseRetention(int tick, int impulseTick, int coastTicks) {
		int elapsed = tick - impulseTick;
		if (elapsed <= 0 || elapsed > coastTicks) {
			return 0.0;
		}
		return switch (elapsed) {
			case 1 -> 0.58;
			case 2 -> 0.28;
			default -> 0.08;
		};
	}
}
