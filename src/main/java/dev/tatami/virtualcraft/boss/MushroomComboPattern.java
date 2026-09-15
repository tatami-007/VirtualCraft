package dev.tatami.virtualcraft.boss;

import java.util.List;

/** Authored mixed-direction combos. Spaces in the notation become deliberate slow beats. */
public final class MushroomComboPattern {
	public static final int LOW_PATTERN_COUNT = 5;
	public static final int MID_PATTERN_COUNT = 5;
	public static final int PATTERN_COUNT = 12;
	private static final int[] MID_VARIANTS = {5, 6, 7, 8, 9};
	private static final int[] LOW_VARIANTS = {0, 1, 3};
	private static final int[] MIXED_VARIANTS = {2, 4, 10, 11};
	private static final int ACTIVE_TICKS = 4;
	private static final int CUE_TICKS = 9;
	private static final int RECOVERY_TICKS = 14;

	private static final List<MushroomComboPattern> PATTERNS = List.of(
			pattern("下下 下", step(Move.ROOT_STOMP_LEFT, 24),
					step(Move.ROOT_STOMP_RIGHT, 36), step(Move.ROOT_CLAMP, 56)),
			pattern("下下下", step(Move.ROOT_STOMP_LEFT, 24),
					step(Move.ROOT_STOMP_RIGHT, 36), step(Move.CAP_QUAKE, 48)),
			pattern("下下下上", step(Move.ROOT_STOMP_LEFT, 24),
					step(Move.ROOT_STOMP_RIGHT, 36), step(Move.MYCELIUM_ERUPTION, 48),
					step(Move.CANOPY_CLOSURE, 62)),
			pattern("下下 下 下", step(Move.ROOT_STOMP_RIGHT, 24),
					step(Move.ROOT_CLAMP, 36), step(Move.ROOT_SURGE, 56),
					step(Move.CAP_QUAKE, 76)),
			pattern("下下 下上", step(Move.ROOT_STOMP_RIGHT, 24),
					step(Move.CAP_QUAKE, 36), step(Move.ROOT_CLAMP, 56),
					step(Move.SPORE_BREATH, 70)),
			pattern("中中 中", step(Move.ROOT_SWEEP_LEFT, 22),
					step(Move.CAP_CLAP, 36), step(Move.STEM_HAMMER, 56)),
			pattern("中中中", step(Move.CAP_CLAP, 22),
					step(Move.ROOT_SWEEP_RIGHT, 36), step(Move.CAP_DRIVE, 50)),
			pattern("中中 中", step(Move.ROOT_SWEEP_RIGHT, 22),
					step(Move.ROOT_SWEEP_LEFT, 36), step(Move.ROOT_UPPERCUT, 56)),
			pattern("中中中", step(Move.SHOULDER_RUSH, 22),
					step(Move.STEM_HAMMER, 36), step(Move.ROOT_SWEEP_RIGHT, 50)),
			pattern("中中 中", step(Move.ROOT_UPPERCUT, 22),
					step(Move.SHOULDER_RUSH, 36), step(Move.CAP_CLAP, 56)),
			pattern("下下下上", step(Move.ROOT_SURGE, 24),
					step(Move.ROOT_STOMP_RIGHT, 36), step(Move.MYCELIUM_ERUPTION, 48),
					step(Move.SPORE_BREATH, 62)),
			pattern("下下 下上", step(Move.ROOT_STOMP_RIGHT, 24),
					step(Move.ROOT_CLAMP, 36), step(Move.CAP_QUAKE, 56),
					step(Move.CANOPY_CLOSURE, 70)));
	private static final int[] ENCOUNTER_ORDER = {5, 2, 6, 4, 7, 10, 8, 0, 9, 11, 1, 3};

	private final String notation;
	private final List<Step> steps;

	private MushroomComboPattern(String notation, List<Step> steps) {
		this.notation = notation;
		this.steps = steps;
	}

	public static MushroomComboPattern forAttack(int attackId, int variant) {
		if (isComboAttack(attackId)) {
			return PATTERNS.get(Math.floorMod(variant, PATTERN_COUNT));
		}
		return null;
	}

	public static int randomVariant(int attackId, int randomValue) {
		if (isComboAttack(attackId)) {
			return Math.floorMod(randomValue, PATTERN_COUNT);
		}
		return Math.floorMod(randomValue, 8);
	}

	public static boolean isComboAttack(int attackId) {
		return attackId == MushroomKingAttackRules.MYCELIAL_SURGE
				|| attackId == MushroomKingAttackRules.CANOPY_CLOSURE
				|| attackId == MushroomKingAttackRules.ROOT_CADENCE;
	}

	public static int variantForAttackSequence(int attackId, int sequenceIndex) {
		int[] family = switch (attackId) {
			case MushroomKingAttackRules.ROOT_CADENCE -> MID_VARIANTS;
			case MushroomKingAttackRules.MYCELIAL_SURGE -> LOW_VARIANTS;
			case MushroomKingAttackRules.CANOPY_CLOSURE -> MIXED_VARIANTS;
			default -> null;
		};
		if (family == null) {
			return randomVariant(attackId, sequenceIndex);
		}
		return family[Math.floorMod(sequenceIndex, family.length)];
	}

	public static int attackForSequence(int sequenceIndex) {
		int variant = patternIndexForSequence(sequenceIndex);
		if (contains(MID_VARIANTS, variant)) {
			return MushroomKingAttackRules.ROOT_CADENCE;
		}
		if (contains(LOW_VARIANTS, variant)) {
			return MushroomKingAttackRules.MYCELIAL_SURGE;
		}
		return MushroomKingAttackRules.CANOPY_CLOSURE;
	}

	public static int variantForSequence(int sequenceIndex) {
		return patternIndexForSequence(sequenceIndex);
	}

	private static int patternIndexForSequence(int sequenceIndex) {
		return ENCOUNTER_ORDER[Math.floorMod(sequenceIndex, ENCOUNTER_ORDER.length)];
	}

	private static boolean contains(int[] variants, int value) {
		for (int variant : variants) {
			if (variant == value) {
				return true;
			}
		}
		return false;
	}

	public String notation() {
		return notation;
	}

	public int stepCount() {
		return steps.size();
	}

	public Step step(int index) {
		return steps.get(Math.max(0, Math.min(steps.size() - 1, index)));
	}

	public int cueTick(int index) {
		int impact = step(index).impactTick();
		if (index == 0) {
			return Math.max(1, impact - CUE_TICKS);
		}
		return Math.max(steps.get(index - 1).impactTick() + 3, impact - CUE_TICKS);
	}

	public int currentStepIndex(int tick) {
		for (int index = 0; index < steps.size(); index++) {
			if (tick <= steps.get(index).impactTick() + ACTIVE_TICKS - 1) {
				return index;
			}
		}
		return steps.size() - 1;
	}

	public int cueStepIndex(int tick) {
		int result = 0;
		for (int index = 1; index < steps.size(); index++) {
			if (tick < cueTick(index)) {
				break;
			}
			result = index;
		}
		return result;
	}

	public int activeStepAt(int tick) {
		for (int index = 0; index < steps.size(); index++) {
			int impact = steps.get(index).impactTick();
			if (tick >= impact && tick < impact + ACTIVE_TICKS) {
				return index;
			}
		}
		return -1;
	}

	public BossAttackDirection directionAt(int tick) {
		return step(cueStepIndex(tick)).direction();
	}

	public int nextImpactAt(int tick) {
		for (Step step : steps) {
			if (step.impactTick() >= tick) {
				return step.impactTick();
			}
		}
		return -1;
	}

	/** The current beat keeps tracking independently until shortly before impact. */
	public int lockTick(int index) {
		return step(index).impactTick();
	}

	public int endTick() {
		return steps.getLast().impactTick() + RECOVERY_TICKS;
	}

	public boolean contains(BossAttackDirection direction) {
		return steps.stream().anyMatch(step -> step.direction() == direction);
	}

	/** Start of the current root beat along the locked attack axis. */
	public double advanceStart(int index) {
		return switch (Math.max(0, index)) {
			case 0 -> 0.35;
			case 1 -> 0.6;
			case 2 -> 0.85;
			default -> 1.1;
		};
	}

	/** Length overlaps the previous beat so the growth reads as one advancing root. */
	public double advanceLength(int index) {
		return index >= 3 ? 4.25 : 4.65;
	}

	private static MushroomComboPattern pattern(String notation, Step... steps) {
		return new MushroomComboPattern(notation, List.of(steps));
	}

	private static Step step(Move move, int impactTick) {
		return new Step(move, impactTick);
	}

	public enum Move {
		ROOT_SWEEP_LEFT(BossAttackDirection.MID),
		ROOT_SWEEP_RIGHT(BossAttackDirection.MID),
		CAP_CLAP(BossAttackDirection.MID),
		STEM_HAMMER(BossAttackDirection.MID),
		CAP_DRIVE(BossAttackDirection.MID),
		ROOT_UPPERCUT(BossAttackDirection.MID),
		SHOULDER_RUSH(BossAttackDirection.MID),
		ROOT_STOMP_LEFT(BossAttackDirection.LOW),
		ROOT_STOMP_RIGHT(BossAttackDirection.LOW),
		ROOT_SURGE(BossAttackDirection.LOW),
		ROOT_CLAMP(BossAttackDirection.LOW),
		CAP_QUAKE(BossAttackDirection.LOW),
		MYCELIUM_ERUPTION(BossAttackDirection.LOW),
		CANOPY_CLOSURE(BossAttackDirection.HIGH),
		SPORE_BREATH(BossAttackDirection.HIGH);

		private final BossAttackDirection direction;

		Move(BossAttackDirection direction) {
			this.direction = direction;
		}

		public BossAttackDirection direction() {
			return direction;
		}
	}

	public record Step(Move move, int impactTick) {
		public BossAttackDirection direction() {
			return move.direction();
		}
	}
}
