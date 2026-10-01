package dev.tatami.virtualcraft.client.render;

import dev.tatami.virtualcraft.boss.MushroomComboPattern.Move;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;

/** Exhaustive presentation routing: a new combat move must choose a visual. */
public enum MushroomEffectStyle {
	SWEEP_LEFT, SWEEP_RIGHT, CLAP, HAMMER, DRIVE, UPPERCUT, RUSH,
	STOMP_LEFT, STOMP_RIGHT, SURGE, CLAMP, QUAKE, ERUPTION, CANOPY, BREATH,
	NEEDLES, CONVERGENCE;

	public static MushroomEffectStyle forMove(Move move) {
		return switch (move) {
			case ROOT_SWEEP_LEFT -> SWEEP_LEFT;
			case ROOT_SWEEP_RIGHT -> SWEEP_RIGHT;
			case CAP_CLAP -> CLAP;
			case STEM_HAMMER -> HAMMER;
			case CAP_DRIVE -> DRIVE;
			case ROOT_UPPERCUT -> UPPERCUT;
			case SHOULDER_RUSH -> RUSH;
			case ROOT_STOMP_LEFT -> STOMP_LEFT;
			case ROOT_STOMP_RIGHT -> STOMP_RIGHT;
			case ROOT_SURGE -> SURGE;
			case ROOT_CLAMP -> CLAMP;
			case CAP_QUAKE -> QUAKE;
			case MYCELIUM_ERUPTION -> ERUPTION;
			case CANOPY_CLOSURE -> CANOPY;
			case SPORE_BREATH -> BREATH;
		};
	}

	public static MushroomEffectStyle forAttack(int attack, int wave) {
		return switch (attack) {
			case MushroomKingAttackRules.ROOT_CADENCE -> wave == 2 ? SWEEP_RIGHT : SWEEP_LEFT;
			case MushroomKingAttackRules.STEM_HAMMER -> HAMMER;
			case MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY -> NEEDLES;
			case MushroomKingAttackRules.CAP_CLAP -> CLAP;
			case MushroomKingAttackRules.CANOPY_CLOSURE -> CANOPY;
			case MushroomKingAttackRules.SPORE_BREATH -> BREATH;
			case MushroomKingAttackRules.MYCELIAL_SURGE -> SURGE;
			case MushroomKingAttackRules.ROOT_CONVERGENCE -> CONVERGENCE;
			default -> throw new IllegalArgumentException("Unknown mushroom attack " + attack);
		};
	}

	public static float releaseFade(float age, float duration) {
		if (age < 0 || duration <= 0) return 0;
		return Math.max(0, 1 - age / duration);
	}
}
