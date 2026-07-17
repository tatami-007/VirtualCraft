package dev.tatami.virtualcraft.boss;

public enum SnakeHuntPattern {
	BITE(Move.BITE),
	TAIL(Move.TAIL),
	BITE_BITE_BITE(Move.BITE, Move.BITE, Move.BITE),
	BITE_BITE_TAIL(Move.BITE, Move.BITE, Move.TAIL),
	BITE_TAIL_BITE(Move.BITE, Move.TAIL, Move.BITE);

	public static final int SEGMENT_TICKS = 20;
	public static final int BITE_HIT_TICK = 11;
	public static final int TAIL_HIT_TICK = 19;
	public static final int ACTIVE_WINDOW_TICKS = 3;

	private final Move[] moves;

	SnakeHuntPattern(Move... moves) {
		this.moves = moves;
	}

	public int segmentCount() {
		return moves.length;
	}

	public int durationTicks() {
		return moves.length * SEGMENT_TICKS + 8;
	}

	public Move move(int segment) {
		return moves[Math.max(0, Math.min(moves.length - 1, segment))];
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

	public enum Move {
		BITE,
		TAIL
	}
}
