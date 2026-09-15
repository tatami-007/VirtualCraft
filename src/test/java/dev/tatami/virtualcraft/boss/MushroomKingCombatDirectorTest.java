package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MushroomKingCombatDirectorTest {
	@Test
	void individualCooldownAndDistanceCanForceRootCadence() {
		int[] ready = blockedAttacks();
		ready[MushroomKingAttackRules.ROOT_CADENCE] = 0;
		assertEquals(MushroomKingAttackRules.ROOT_CADENCE,
				MushroomKingCombatDirector.select(context(3.0, ready, new int[0], 0), 0.5));
	}

	@Test
	void comboEngineCanSelectAnotherPatternAfterPreviousCombo() {
		int[] ready = blockedAttacks();
		ready[MushroomKingAttackRules.ROOT_CADENCE] = 0;
		assertEquals(MushroomKingAttackRules.ROOT_CADENCE,
				MushroomKingCombatDirector.select(context(3.0, ready,
				new int[] {MushroomKingAttackRules.ROOT_CADENCE},
				MushroomKingAttackRules.ROOT_CADENCE), 0.5));
	}

	@Test
	void readyAlternativePreventsRepeatingTheSameComboFamily() {
		int[] ready = blockedAttacks();
		ready[MushroomKingAttackRules.ROOT_CADENCE] = 0;
		ready[MushroomKingAttackRules.MYCELIAL_SURGE] = 0;
		for (int sample = 0; sample < 20; sample++) {
			assertEquals(MushroomKingAttackRules.MYCELIAL_SURGE,
					MushroomKingCombatDirector.select(context(3.0, ready,
							new int[] {MushroomKingAttackRules.ROOT_CADENCE},
							MushroomKingAttackRules.ROOT_CADENCE), sample / 20.0));
		}
	}

	@Test
	void directorExposesAllThreeComboFamiliesAndNoLegacyStandaloneAttack() {
		for (int attack = 1; attack <= MushroomKingCombatDirector.ATTACK_COUNT; attack++) {
			int[] ready = blockedAttacks();
			ready[attack] = 0;
			int selected = MushroomKingCombatDirector.select(
					context(3.0, ready, new int[0], 0), 0.5);
			boolean comboFamily = attack == MushroomKingAttackRules.ROOT_CADENCE
					|| attack == MushroomKingAttackRules.MYCELIAL_SURGE
					|| attack == MushroomKingAttackRules.CANOPY_CLOSURE;
			assertEquals(comboFamily ? attack : 0, selected);
		}
	}

	@Test
	void ultimateIsLockedUntilTheEncounterHasSettled() {
		int[] ready = blockedAttacks();
		ready[MushroomKingAttackRules.ROOT_CONVERGENCE] = 0;
		assertEquals(0, MushroomKingCombatDirector.select(
				context(5.0, 499, ready, new int[0], 0), 0.5));
		assertEquals(MushroomKingAttackRules.ROOT_CONVERGENCE,
				MushroomKingCombatDirector.select(
						context(5.0, 500, ready, new int[0], 0), 0.5));
	}

	private static MushroomKingCombatDirector.Context context(double distance, int[] ready,
			int[] recent, int last) {
		return context(distance, 400, ready, recent, last);
	}

	private static MushroomKingCombatDirector.Context context(double distance, int combatTicks,
			int[] ready, int[] recent, int last) {
		return new MushroomKingCombatDirector.Context(distance, 100, combatTicks,
				new int[BossAttackDirection.values().length], ready, recent, last, 1, 1.0);
	}

	private static int[] readyAttacks() {
		return new int[MushroomKingCombatDirector.ATTACK_COUNT + 1];
	}

	private static int[] blockedAttacks() {
		int[] ready = readyAttacks();
		Arrays.fill(ready, 500);
		return ready;
	}
}
