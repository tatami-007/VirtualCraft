package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MushroomComboPatternTest {
	@Test
	void midCombosNeverContainProjectileDirections() {
		for (int variant = 0; variant < MushroomComboPattern.MID_PATTERN_COUNT; variant++) {
			MushroomComboPattern combo = MushroomComboPattern.forAttack(
					MushroomKingAttackRules.ROOT_CADENCE,
					MushroomComboPattern.LOW_PATTERN_COUNT + variant);
			for (int step = 0; step < combo.stepCount(); step++) {
				assertEquals(BossAttackDirection.MID, combo.step(step).direction());
			}
		}
	}

	@Test
	void lowFamilyContainsFiveRequestedRhythms() {
		String[] expected = {"下下 下", "下下下", "下下下上", "下下 下 下", "下下 下上"};
		for (int variant = 0; variant < expected.length; variant++) {
			MushroomComboPattern pattern = MushroomComboPattern.forAttack(
					MushroomKingAttackRules.MYCELIAL_SURGE, variant);
			assertEquals(expected[variant], pattern.notation());
		}
	}

	@Test
	void lowFamilyUsesFiveVisuallyDistinctPhysicalMoves() {
		Set<MushroomComboPattern.Move> moves = new HashSet<>();
		for (int variant : new int[] {0, 1, 3}) {
			MushroomComboPattern combo = MushroomComboPattern.forAttack(
					MushroomKingAttackRules.MYCELIAL_SURGE, variant);
			for (int step = 0; step < combo.stepCount(); step++) {
				if (combo.step(step).direction() == BossAttackDirection.LOW) {
					moves.add(combo.step(step).move());
				}
			}
		}
		assertTrue(moves.contains(MushroomComboPattern.Move.ROOT_STOMP_LEFT));
		assertTrue(moves.contains(MushroomComboPattern.Move.ROOT_STOMP_RIGHT));
		assertTrue(moves.contains(MushroomComboPattern.Move.ROOT_SURGE));
		assertTrue(moves.contains(MushroomComboPattern.Move.ROOT_CLAMP));
		assertTrue(moves.contains(MushroomComboPattern.Move.CAP_QUAKE));
	}

	@Test
	void encounterIncludesEveryLowAttackSilhouette() {
		Set<MushroomComboPattern.Move> moves = new HashSet<>();
		for (int variant = 0; variant < MushroomComboPattern.PATTERN_COUNT; variant++) {
			MushroomComboPattern combo = MushroomComboPattern.forAttack(
					MushroomKingAttackRules.ROOT_CADENCE, variant);
			for (int step = 0; step < combo.stepCount(); step++) {
				if (combo.step(step).direction() == BossAttackDirection.LOW) {
					moves.add(combo.step(step).move());
				}
			}
		}
		assertEquals(Set.of(MushroomComboPattern.Move.ROOT_STOMP_LEFT,
				MushroomComboPattern.Move.ROOT_STOMP_RIGHT,
				MushroomComboPattern.Move.ROOT_SURGE,
				MushroomComboPattern.Move.ROOT_CLAMP,
				MushroomComboPattern.Move.CAP_QUAKE,
				MushroomComboPattern.Move.MYCELIUM_ERUPTION), moves);
	}

	@Test
	void midFamilyContainsTwoRequestedRhythms() {
		MushroomComboPattern paused = MushroomComboPattern.forAttack(
				MushroomKingAttackRules.ROOT_CADENCE, MushroomComboPattern.LOW_PATTERN_COUNT);
		assertEquals("中中 中", paused.notation());
		assertEquals(MushroomComboPattern.Move.ROOT_SWEEP_LEFT, paused.step(0).move());
		assertEquals(MushroomComboPattern.Move.CAP_CLAP, paused.step(1).move());
		assertEquals(MushroomComboPattern.Move.STEM_HAMMER, paused.step(2).move());

		MushroomComboPattern fast = MushroomComboPattern.forAttack(
				MushroomKingAttackRules.ROOT_CADENCE, MushroomComboPattern.LOW_PATTERN_COUNT + 1);
		assertEquals("中中中", fast.notation());
		assertEquals(MushroomComboPattern.Move.CAP_CLAP, fast.step(0).move());
		assertEquals(MushroomComboPattern.Move.ROOT_SWEEP_RIGHT, fast.step(1).move());
		assertEquals(MushroomComboPattern.Move.CAP_DRIVE, fast.step(2).move());
	}

	@Test
	void repeatedMidDirectionsUseSeveralPhysicalMoves() {
		Set<MushroomComboPattern.Move> moves = new HashSet<>();
		for (int variant = 0; variant < MushroomComboPattern.MID_PATTERN_COUNT; variant++) {
			MushroomComboPattern combo = MushroomComboPattern.forAttack(
					MushroomKingAttackRules.ROOT_CADENCE,
					MushroomComboPattern.LOW_PATTERN_COUNT + variant);
			for (int step = 0; step < combo.stepCount(); step++) {
				moves.add(combo.step(step).move());
			}
		}
		assertEquals(Set.of(MushroomComboPattern.Move.ROOT_SWEEP_LEFT,
				MushroomComboPattern.Move.ROOT_SWEEP_RIGHT,
				MushroomComboPattern.Move.CAP_CLAP,
				MushroomComboPattern.Move.STEM_HAMMER,
				MushroomComboPattern.Move.CAP_DRIVE,
				MushroomComboPattern.Move.ROOT_UPPERCUT,
				MushroomComboPattern.Move.SHOULDER_RUSH), moves);
	}

	@Test
	void spacesCreateLongerPausesAndMixedFinishChangesDirection() {
		MushroomComboPattern paused = MushroomComboPattern.forAttack(
				MushroomKingAttackRules.MYCELIAL_SURGE, 0);
		assertEquals(20, paused.step(2).impactTick() - paused.step(1).impactTick());

		MushroomComboPattern mixed = MushroomComboPattern.forAttack(
				MushroomKingAttackRules.MYCELIAL_SURGE, 2);
		assertTrue(mixed.contains(BossAttackDirection.HIGH));
		assertEquals(BossAttackDirection.HIGH, mixed.step(3).direction());
	}

	@Test
	void midTimelinesMatchAuthoredParryRhythms() {
		MushroomComboPattern paused = MushroomComboPattern.forAttack(
				MushroomKingAttackRules.ROOT_CADENCE, MushroomComboPattern.LOW_PATTERN_COUNT);
		assertEquals(22, paused.step(0).impactTick());
		assertEquals(13, paused.cueTick(0));
		assertEquals(36, paused.step(1).impactTick());
		assertEquals(56, paused.step(2).impactTick());

		MushroomComboPattern fast = MushroomComboPattern.forAttack(
				MushroomKingAttackRules.ROOT_CADENCE, MushroomComboPattern.LOW_PATTERN_COUNT + 1);
		assertEquals(50, fast.step(2).impactTick());
		assertEquals(fast.step(1).impactTick(), fast.lockTick(1));
	}

	@Test
	void lowComboBeatsAdvanceAlongOneContinuousRootPath() {
		MushroomComboPattern pattern = MushroomComboPattern.forAttack(
				MushroomKingAttackRules.MYCELIAL_SURGE, 3);
		assertEquals(0.35, pattern.advanceStart(0), 0.0001);
		assertEquals(0.6, pattern.advanceStart(1), 0.0001);
		assertEquals(0.85, pattern.advanceStart(2), 0.0001);
		assertEquals(1.1, pattern.advanceStart(3), 0.0001);
		assertTrue(pattern.advanceLength(0) > 4.5);
	}

	@Test
	void encounterSequenceCoversEveryComboBeforeRepeating() {
		String[] expected = {"中中 中", "下下下上", "中中中", "下下 下上",
				"中中 中", "下下下上", "中中中", "下下 下",
				"中中 中", "下下 下上", "下下下", "下下 下 下"};
		for (int index = 0; index < expected.length; index++) {
			int attack = MushroomComboPattern.attackForSequence(index);
			int variant = MushroomComboPattern.variantForSequence(index);
			assertEquals(expected[index], MushroomComboPattern.forAttack(attack, variant).notation());
		}
	}

	@Test
	void eachCombatFamilyCyclesOnlyItsAuthoredVariants() {
		Set<Integer> mid = new HashSet<>();
		Set<Integer> low = new HashSet<>();
		Set<Integer> mixed = new HashSet<>();
		for (int index = 0; index < 20; index++) {
			mid.add(MushroomComboPattern.variantForAttackSequence(
					MushroomKingAttackRules.ROOT_CADENCE, index));
			low.add(MushroomComboPattern.variantForAttackSequence(
					MushroomKingAttackRules.MYCELIAL_SURGE, index));
			mixed.add(MushroomComboPattern.variantForAttackSequence(
					MushroomKingAttackRules.CANOPY_CLOSURE, index));
		}
		assertEquals(Set.of(5, 6, 7, 8, 9), mid);
		assertEquals(Set.of(0, 1, 3), low);
		assertEquals(Set.of(2, 4, 10, 11), mixed);
	}
}
