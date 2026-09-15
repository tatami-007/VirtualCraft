package dev.tatami.virtualcraft.boss;

public enum SnakeHuntPattern {
	BITE(Move.BITE),
	TAIL(Move.TAIL),
	BITE_BITE_BITE(Move.BITE, Move.BITE, Move.BITE),
	BITE_BITE_TAIL(Move.BITE, Move.BITE, Move.TAIL),
	BITE_TAIL_BITE(Move.BITE, Move.TAIL, Move.BITE);

	public static final int COMBO_OPENING_TICKS = 12;
	public static final int SEGMENT_TICKS = 20;
	public static final int TAIL_SEGMENT_TICKS = 32;
	public static final int COMBO_BITE_LUNGE_TICK = 9;
	public static final int COMBO_BITE_HIT_TICK = 12;
	public static final int BITE_LUNGE_TICK = 13;
	public static final int BITE_HIT_TICK = 16;
	public static final int TAIL_RELEASE_START_TICK = 12;
	public static final int TAIL_ACTIVE_START_TICK = TAIL_RELEASE_START_TICK;
	public static final int TAIL_HIT_TICK = 31;
	public static final int ACTIVE_WINDOW_TICKS = 3;

	private final Move[] moves;

	SnakeHuntPattern(Move... moves) {
		this.moves = moves;
	}

	public int segmentCount() {
		return moves.length;
	}

	public int durationTicks() {
		int duration = COMBO_OPENING_TICKS + 8;
		for (int segment = 0; segment < moves.length; segment++) {
			duration += segmentDuration(segment);
		}
		return duration;
	}

	public Move move(int segment) {
		return moves[Math.max(0, Math.min(moves.length - 1, segment))];
	}

	public int segmentDuration(int segment) {
		return move(segment) == Move.TAIL ? TAIL_SEGMENT_TICKS : SEGMENT_TICKS;
	}

	public int comboSegmentAt(int attackTick) {
		int elapsed = Math.max(0, attackTick - COMBO_OPENING_TICKS);
		for (int segment = 0; segment < moves.length; segment++) {
			int duration = segmentDuration(segment);
			if (elapsed < duration) {
				return segment;
			}
			elapsed -= duration;
		}
		return moves.length;
	}

	public int comboSegmentStartTick(int segment) {
		int start = COMBO_OPENING_TICKS;
		for (int index = 0; index < Math.min(segment, moves.length); index++) {
			start += segmentDuration(index);
		}
		return start;
	}

	public int comboLocalTickAt(int attackTick) {
		int segment = comboSegmentAt(attackTick);
		return attackTick - comboSegmentStartTick(segment);
	}

	public static SnakeHuntPattern byId(int id) {
		SnakeHuntPattern[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, id))];
	}

	public static int firstComboId() {
		return BITE_BITE_BITE.ordinal();
	}

	public static int comboCount() {
		return values().length - firstComboId();
	}

	public static int nextComboId(int current) {
		return firstComboId() + (current - firstComboId() + 1) % comboCount();
	}

	public static int segment(int attackTick) {
		return Math.max(0, attackTick) / SEGMENT_TICKS;
	}

	public static int localTick(int attackTick) {
		return Math.max(0, attackTick) % SEGMENT_TICKS;
	}

	public static boolean isComboOpening(int attackTick) {
		return attackTick < COMBO_OPENING_TICKS;
	}

	public static int comboSegment(int attackTick) {
		return segment(Math.max(0, attackTick - COMBO_OPENING_TICKS));
	}

	public static int comboLocalTick(int attackTick) {
		return isComboOpening(attackTick) ? -1
				: localTick(attackTick - COMBO_OPENING_TICKS);
	}

	/**
	 * Relative sweep angle shared by the multipart hitboxes and client telegraph.
	 * The tail first coils farther behind the snake, then releases in a short whip.
	 */
	public static double tailSweepPhase(double localTick) {
		if (localTick <= 3.0) {
			return lerp(0.0, -1.05, smooth(clamp(localTick / 3.0)));
		}
		if (localTick <= TAIL_RELEASE_START_TICK) {
			double coil = clamp((localTick - 3.0) / (TAIL_RELEASE_START_TICK - 3.0));
			return lerp(-1.05, -1.55, smooth(coil));
		}
		double release = clamp((localTick - TAIL_RELEASE_START_TICK)
				/ (TAIL_HIT_TICK - (double)TAIL_RELEASE_START_TICK));
		return lerp(-1.55, 1.55, smooth(release));
	}

	/**
	 * Absolute yaw for the three visible tail joints. Blending the delayed
	 * phases keeps the chain continuous while still giving the tip a whip-like
	 * delay instead of folding it back through the body.
	 */
	public static TailPose tailPose(double localTick) {
		double rootPhase = tailSweepPhase(localTick);
		double middlePhase = tailSweepPhase(Math.max(0.0, localTick - 1.5));
		double tipPhase = tailSweepPhase(Math.max(0.0, localTick - 3.0));
		return new TailPose(
				rootPhase,
				rootPhase * 0.55 + middlePhase * 0.42,
				rootPhase * 0.35 + middlePhase * 0.32 + tipPhase * 0.28);
	}

	public static TailPose tailRecoveryPose(double recoveryTick) {
		double progress = smooth(clamp(recoveryTick / 5.0));
		double remaining = 1.0 - progress;
		TailPose impact = tailPose(TAIL_HIT_TICK);
		return new TailPose(
				impact.root() * remaining,
				impact.middle() * remaining,
				impact.tip() * remaining);
	}

	/**
	 * The complete body turn used by a tail strike. The tail still has its own
	 * delayed chain pose, but the attack is driven by a full-body rotation
	 * instead of three isolated tail cubes twitching in place.
	 */
	public static double tailBodySpinPhase(double localTick) {
		double progress = clamp((localTick - TAIL_RELEASE_START_TICK)
				/ (TAIL_HIT_TICK - (double)TAIL_RELEASE_START_TICK));
		return Math.PI * 2.0 * smooth(progress);
	}

	public static float tailSweepDirection(boolean sweepLeft) {
		return sweepLeft ? -1.0F : 1.0F;
	}

	public static boolean isTailDamageActive(int localTick) {
		return localTick >= TAIL_ACTIVE_START_TICK && localTick <= TAIL_HIT_TICK;
	}

	private static double smooth(double value) {
		double clamped = clamp(value);
		return clamped * clamped * (3.0 - 2.0 * clamped);
	}

	private static double lerp(double start, double end, double amount) {
		return start + (end - start) * amount;
	}

	private static double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	public enum Move {
		BITE,
		TAIL
	}

	public record TailPose(double root, double middle, double tip) {
	}
}
