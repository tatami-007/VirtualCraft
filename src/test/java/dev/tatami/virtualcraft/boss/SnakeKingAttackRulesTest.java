package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SnakeKingAttackRulesTest {
	@Test
	void everySnakeKingAttackHasExactlyOneDirection() {
		BossAttackDirection[] expected = {
				BossAttackDirection.MID,
				BossAttackDirection.MID,
				BossAttackDirection.MID,
				BossAttackDirection.HIGH,
				BossAttackDirection.LOW,
				BossAttackDirection.HIGH,
				BossAttackDirection.MID,
				BossAttackDirection.LOW,
				BossAttackDirection.LOW,
				BossAttackDirection.HIGH
		};
		for (int attackId = 1; attackId <= expected.length; attackId++) {
			assertEquals(expected[attackId - 1], SnakeKingAttackRules.directionFor(attackId));
		}
	}
}
