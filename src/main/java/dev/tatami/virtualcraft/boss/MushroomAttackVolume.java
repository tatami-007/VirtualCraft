package dev.tatami.virtualcraft.boss;

/** Geometry shared by Mushroom King server damage and client telegraphs. */
public final class MushroomAttackVolume {
	public static final double HIGH_MIN_Y = 1.05;
	public static final double HIGH_MAX_Y = 3.35;
	public static final double NEEDLE_LENGTH = 11.0;
	public static final double NEEDLE_HALF_WIDTH = 0.72;
	public static final double BREATH_LENGTH = 9.5;
	public static final double BREATH_HALF_WIDTH = 2.5;
	public static final double SURGE_LENGTH = 10.0;
	public static final double SURGE_HALF_WIDTH = 1.0;
	public static final double CONVERGENCE_HALF_WIDTH = 0.9;
	public static final double CONVERGENCE_ARENA_RADIUS = 20.0;

	private MushroomAttackVolume() {
	}

	public static double radius(int attack, int wave) {
		int clampedWave = Math.max(1, Math.min(3, wave));
		return switch (attack) {
			case MushroomKingAttackRules.ROOT_CADENCE -> 4.6 + clampedWave * 0.2;
			case MushroomKingAttackRules.STEM_HAMMER -> 5.6;
			case MushroomKingAttackRules.CAP_CLAP -> 5.1;
			case MushroomKingAttackRules.CANOPY_CLOSURE -> 6.2;
			case MushroomKingAttackRules.ROOT_CONVERGENCE -> CONVERGENCE_ARENA_RADIUS;
			default -> 0.0;
		};
	}

	public static double angleDegrees(int attack, int wave) {
		return switch (attack) {
			case MushroomKingAttackRules.ROOT_CADENCE -> wave == 3 ? 118.0 : 82.0;
			case MushroomKingAttackRules.STEM_HAMMER -> 104.0;
			case MushroomKingAttackRules.CAP_CLAP -> 88.0;
			case MushroomKingAttackRules.CANOPY_CLOSURE -> 148.0;
			default -> 360.0;
		};
	}

	public static double surgeStart(int wave) {
		return switch (Math.max(1, Math.min(3, wave))) {
			case 1 -> 0.35;
			case 2 -> 3.2;
			default -> 6.05;
		};
	}

	public static double surgeLength(int wave) {
		return wave == 3 ? 4.0 : 4.7;
	}

	public static double convergenceGapAngle(int variant) {
		return Math.floorMod(variant, 8) * 45.0;
	}
}
