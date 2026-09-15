package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class MushroomKingAttackRulesTest {
	@Test
	void eightAttacksUseTwoHighFourMidAndTwoLowDirections() {
		EnumMap<BossAttackDirection, Integer> counts = new EnumMap<>(BossAttackDirection.class);
		for (BossAttackDirection direction : BossAttackDirection.values()) {
			counts.put(direction, 0);
		}
		for (int attack = 1; attack <= MushroomKingAttackRules.ATTACK_COUNT; attack++) {
			counts.compute(MushroomKingAttackRules.directionFor(attack), (key, value) -> value + 1);
		}
		assertEquals(2, counts.get(BossAttackDirection.HIGH));
		assertEquals(4, counts.get(BossAttackDirection.MID));
		assertEquals(2, counts.get(BossAttackDirection.LOW));
	}

	@Test
	void areaClassificationMatchesPersistentFungalVolumes() {
		assertFalse(MushroomKingAttackRules.isAreaAttack(MushroomKingAttackRules.ROOT_CADENCE));
		assertFalse(MushroomKingAttackRules.isAreaAttack(MushroomKingAttackRules.CANOPY_CLOSURE));
		assertTrue(MushroomKingAttackRules.isAreaAttack(MushroomKingAttackRules.SPORE_BREATH));
		assertTrue(MushroomKingAttackRules.isAreaAttack(MushroomKingAttackRules.MYCELIAL_SURGE));
		assertTrue(MushroomKingAttackRules.isAreaAttack(MushroomKingAttackRules.ROOT_CONVERGENCE));
	}

	@Test
	void multiWaveAttacksKeepTheirIntendedRhythm() {
		assertEquals(24, MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.ROOT_CADENCE, 1));
		assertEquals(14, MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.ROOT_CADENCE, 2)
				- MushroomKingAttackRules.impactTick(MushroomKingAttackRules.ROOT_CADENCE, 1));
		assertEquals(14, MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.ROOT_CADENCE, 3)
				- MushroomKingAttackRules.impactTick(MushroomKingAttackRules.ROOT_CADENCE, 2));
		for (int attack : new int[] {
				MushroomKingAttackRules.ROOT_CADENCE,
				MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY,
				MushroomKingAttackRules.SPORE_BREATH,
				MushroomKingAttackRules.MYCELIAL_SURGE,
				MushroomKingAttackRules.ROOT_CONVERGENCE
		}) {
			int waves = MushroomKingAttackRules.waveCount(attack);
			assertTrue(MushroomKingAttackRules.impactTick(attack, waves)
					< MushroomKingAttackRules.endTick(attack));
		}
	}

	@Test
	void rootConvergenceIsOneUnavoidableLateImpact() {
		assertTrue(MushroomKingAttackRules.isUnavoidableAttack(
				MushroomKingAttackRules.ROOT_CONVERGENCE));
		assertEquals(1, MushroomKingAttackRules.waveCount(
				MushroomKingAttackRules.ROOT_CONVERGENCE));
		assertEquals(60, MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.ROOT_CONVERGENCE, 1));
		assertTrue(MushroomKingAttackRules.endTick(MushroomKingAttackRules.ROOT_CONVERGENCE)
				> MushroomKingAttackRules.impactTick(MushroomKingAttackRules.ROOT_CONVERGENCE, 1));
	}

	@Test
	void targetingLocksEightTicksBeforeFirstImpact() {
		for (int attack = 1; attack <= MushroomKingAttackRules.ATTACK_COUNT; attack++) {
			assertEquals(MushroomKingAttackRules.warningTicks(attack) - 8,
					MushroomKingAttackRules.lockTick(attack));
		}
	}

	@Test
	void repeatedWavesStayActiveForThreeTicksWithoutOverlapping() {
		assertEquals(0, MushroomKingAttackRules.activeWaveAtTick(
				MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY, 27));
		assertEquals(1, MushroomKingAttackRules.activeWaveAtTick(
				MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY, 28));
		assertEquals(1, MushroomKingAttackRules.activeWaveAtTick(
				MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY, 30));
		assertEquals(0, MushroomKingAttackRules.activeWaveAtTick(
				MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY, 31));
		assertEquals(2, MushroomKingAttackRules.activeWaveAtTick(
				MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY, 40));
		assertEquals(3, MushroomKingAttackRules.waveCount(MushroomKingAttackRules.SPORE_BREATH));
		assertEquals(7, MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.SPORE_BREATH, 2)
				- MushroomKingAttackRules.impactTick(MushroomKingAttackRules.SPORE_BREATH, 1));
		assertEquals(7, MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.SPORE_BREATH, 3)
				- MushroomKingAttackRules.impactTick(MushroomKingAttackRules.SPORE_BREATH, 2));
	}
}
