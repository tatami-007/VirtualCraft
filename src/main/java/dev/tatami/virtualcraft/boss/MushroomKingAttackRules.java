package dev.tatami.virtualcraft.boss;

/** Shared timelines for the Mushroom King's body-led attack set. */
public final class MushroomKingAttackRules {
	public static final int ULTIMATE_UNLOCK_COMBAT_TICKS = 500;
	public static final int ROOT_CONVERGENCE_IMPACT_TICK = 60;
	public static final int ROOT_CONVERGENCE_END_TICK = 76;
	public static final int ATTACK_COUNT = 8;
	public static final int ROOT_CADENCE = 1;
	public static final int STEM_HAMMER = 2;
	public static final int SPORE_NEEDLE_VOLLEY = 3;
	public static final int CAP_CLAP = 4;
	public static final int CANOPY_CLOSURE = 5;
	public static final int SPORE_BREATH = 6;
	public static final int MYCELIAL_SURGE = 7;
	public static final int ROOT_CONVERGENCE = 8;

	private MushroomKingAttackRules() {
	}

	public static BossAttackDirection directionFor(int attackId) {
		return switch (attackId) {
			case CANOPY_CLOSURE, SPORE_BREATH -> BossAttackDirection.HIGH;
			case MYCELIAL_SURGE, ROOT_CONVERGENCE -> BossAttackDirection.LOW;
			default -> BossAttackDirection.MID;
		};
	}

	public static boolean isKnownAttack(int attackId) {
		return attackId >= ROOT_CADENCE && attackId <= ROOT_CONVERGENCE;
	}

	public static boolean isCombatAttack(int attackId) {
		return attackId == ROOT_CADENCE
				|| attackId == CANOPY_CLOSURE
				|| attackId == SPORE_BREATH
				|| attackId == MYCELIAL_SURGE
				|| attackId == ROOT_CONVERGENCE;
	}

	/** The ultimate deliberately sits outside the normal high/mid/low response rules. */
	public static boolean isUnavoidableAttack(int attackId) {
		return attackId == ROOT_CONVERGENCE;
	}

	public static int warningTicks(int attackId) {
		return impactTick(attackId, 1);
	}

	public static int lockTick(int attackId) {
		return Math.max(1, warningTicks(attackId) - 8);
	}

	public static int impactTick(int attackId, int wave) {
		int clampedWave = Math.max(1, Math.min(3, wave));
		return switch (attackId) {
			case ROOT_CADENCE -> switch (clampedWave) {
				case 1 -> 24;
				case 2 -> 38;
				default -> 52;
			};
			case STEM_HAMMER -> 34;
			case SPORE_NEEDLE_VOLLEY -> switch (clampedWave) {
				case 1 -> 28;
				case 2 -> 40;
				default -> 52;
			};
			case CAP_CLAP -> 30;
			case CANOPY_CLOSURE -> 30;
			case SPORE_BREATH -> switch (clampedWave) {
				case 1 -> 26;
				case 2 -> 33;
				default -> 40;
			};
			case MYCELIAL_SURGE -> switch (clampedWave) {
				case 1 -> 28;
				case 2 -> 36;
				default -> 44;
			};
			case ROOT_CONVERGENCE -> ROOT_CONVERGENCE_IMPACT_TICK;
			default -> 20;
		};
	}

	public static int activeEndTick(int attackId) {
		return switch (attackId) {
			case SPORE_BREATH -> impactTick(SPORE_BREATH, 3) + 2;
			default -> impactTick(attackId, waveCount(attackId)) + 2;
		};
	}

	public static int endTick(int attackId) {
		return switch (attackId) {
			case ROOT_CADENCE -> 66;
			case STEM_HAMMER -> 52;
			case SPORE_NEEDLE_VOLLEY -> 64;
			case CAP_CLAP -> 48;
			case CANOPY_CLOSURE -> 48;
			case SPORE_BREATH -> 51;
			case MYCELIAL_SURGE -> 58;
			case ROOT_CONVERGENCE -> ROOT_CONVERGENCE_END_TICK;
			default -> 1;
		};
	}

	public static int waveCount(int attackId) {
		return switch (attackId) {
			case ROOT_CADENCE, SPORE_NEEDLE_VOLLEY, SPORE_BREATH, MYCELIAL_SURGE -> 3;
			case ROOT_CONVERGENCE -> 1;
			default -> 1;
		};
	}

	public static int activeWaveAtTick(int attackId, int tick) {
		for (int wave = 1; wave <= waveCount(attackId); wave++) {
			int impact = impactTick(attackId, wave);
			if (tick >= impact && tick <= impact + 2) {
				return wave;
			}
		}
		return 0;
	}

	public static boolean isAreaAttack(int attackId) {
		return attackId == SPORE_BREATH
				|| attackId == MYCELIAL_SURGE
				|| attackId == ROOT_CONVERGENCE;
	}
}
