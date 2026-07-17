package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SnakeKingCombatDirectorTest {
	@Test
	void respectsDistanceAndIndividualCooldowns() {
		int[] ready = new int[SnakeKingCombatDirector.ATTACK_COUNT + 1];
		int[] directionReady = new int[BossAttackDirection.values().length];
		Arrays.fill(ready, 500);
		ready[2] = 0;
		SnakeKingCombatDirector.Context context = new SnakeKingCombatDirector.Context(
				3.0, 100, directionReady, ready, new int[0], 0, 1, 1.0);
		assertEquals(2, SnakeKingCombatDirector.select(context, 0.5));
	}

	@Test
	void sharedHighCooldownExcludesEveryHighAttack() {
		int[] ready = new int[SnakeKingCombatDirector.ATTACK_COUNT + 1];
		int[] directionReady = new int[BossAttackDirection.values().length];
		directionReady[BossAttackDirection.HIGH.ordinal()] = 300;
		SnakeKingCombatDirector.Context context = new SnakeKingCombatDirector.Context(
				12.0, 100, directionReady, ready, new int[0], 0, 1, 1.0);
		for (int i = 0; i < 20; i++) {
			int selected = SnakeKingCombatDirector.select(context, i / 20.0);
			assertEquals(false, SnakeKingAttackRules.directionFor(selected) == BossAttackDirection.HIGH);
		}
	}

	@Test
	void sharedDirectionCooldownExcludesEveryAttackInThatDirection() {
		int[] ready = new int[SnakeKingCombatDirector.ATTACK_COUNT + 1];
		int[] directionReady = new int[BossAttackDirection.values().length];
		directionReady[BossAttackDirection.LOW.ordinal()] = 300;
		SnakeKingCombatDirector.Context context = new SnakeKingCombatDirector.Context(
				6.0, 100, directionReady, ready, new int[0], 0, 1, 1.0);
		for (int i = 0; i < 20; i++) {
			int selected = SnakeKingCombatDirector.select(context, i / 20.0);
			assertEquals(false, SnakeKingAttackRules.directionFor(selected) == BossAttackDirection.LOW);
		}
	}

	@Test
	void queuedFollowUpsRemainDisabled() {
		int[] ready = new int[SnakeKingCombatDirector.ATTACK_COUNT + 1];
		assertEquals(0, SnakeKingCombatDirector.comboFollowUp(1, 4.0, 100, ready, 0.1));
	}

	@Test
	void poisonCooldownsAndCloseRangeMeleeWeightStayPredictable() {
		assertEquals(160, SnakeKingCombatDirector.cooldown(4));
		assertEquals(100, SnakeKingCombatDirector.cooldown(5));

		int[] ready = new int[SnakeKingCombatDirector.ATTACK_COUNT + 1];
		int[] directionReady = new int[BossAttackDirection.values().length];
		SnakeKingCombatDirector.Context context = new SnakeKingCombatDirector.Context(
				4.0, 100, directionReady, ready, new int[0], 0, 1, 1.0);
		int melee = 0;
		for (int i = 0; i < 1000; i++) {
			int attack = SnakeKingCombatDirector.select(context, (i + 0.5) / 1000.0);
			if (attack == 1 || attack == 2 || attack == 3 || attack == 10) {
				melee++;
			}
		}
		assertTrue(melee >= 600, "close-range attacks must remain the main combat axis");
	}

	@Test
	void allTenAttacksCanParticipate() {
		int[] ready = new int[SnakeKingCombatDirector.ATTACK_COUNT + 1];
		int[] directionReady = new int[BossAttackDirection.values().length];
		SnakeKingCombatDirector.Context context = new SnakeKingCombatDirector.Context(
				6.0, 1000, directionReady, ready, new int[0], 0, 1, 1.0);
		Set<Integer> selectedAttacks = new HashSet<>();
		for (int i = 0; i < 10000; i++) {
			int attack = SnakeKingCombatDirector.select(context, (i + 0.5) / 10000.0);
			assertTrue(attack >= 1 && attack <= 10);
			selectedAttacks.add(attack);
		}
		assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), selectedAttacks);
	}

	@Test
	void repeatedCloseCombatStronglyPromotesAStyleChange() {
		int[] ready = new int[SnakeKingCombatDirector.ATTACK_COUNT + 1];
		int[] directionReady = new int[BossAttackDirection.values().length];
		SnakeKingCombatDirector.Context context = new SnakeKingCombatDirector.Context(
				6.0, 1000, directionReady, ready, new int[]{1, 2, 3}, 3, 1, 1.0);
		int closeCombat = 0;
		for (int i = 0; i < 1000; i++) {
			int attack = SnakeKingCombatDirector.select(context, (i + 0.5) / 1000.0);
			if (attack == 1 || attack == 2 || attack == 3 || attack == 10) {
				closeCombat++;
			}
		}
		assertTrue(closeCombat < 350, "three close attacks must strongly favor a different style");
	}
}
