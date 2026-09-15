package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnakeKingAttackRulesTest {
	@Test
	void snakeParryWindowIsTighterThanTheSharedBossDefault() {
		assertEquals(10, SnakeKingAttackRules.MID_PARRY_WINDOW_TICKS);
	}

	@Test
	void corrosiveCoilExpandsContinuouslyFromBossToArenaEdge() {
		assertEquals(SnakeKingAttackRules.COIL_MIN_RADIUS,
				SnakeKingAttackRules.coilRadius(SnakeKingAttackRules.COIL_RELEASE_START), 0.0001);
		assertTrue(SnakeKingAttackRules.coilRadius(52) > SnakeKingAttackRules.COIL_MIN_RADIUS);
		assertTrue(SnakeKingAttackRules.coilRadius(52) < SnakeKingAttackRules.COIL_MAX_RADIUS);
		assertEquals(SnakeKingAttackRules.COIL_MAX_RADIUS,
				SnakeKingAttackRules.coilRadius(SnakeKingAttackRules.COIL_RELEASE_END), 0.0001);
	}
	@Test
	void everySnakeKingAttackHasExactlyOneDirection() {
		BossAttackDirection[] expected = {
				BossAttackDirection.MID,
				BossAttackDirection.MID,
				BossAttackDirection.MID,
				BossAttackDirection.HIGH,
				BossAttackDirection.LOW,
				BossAttackDirection.HIGH,
				BossAttackDirection.LOW,
				BossAttackDirection.LOW,
				BossAttackDirection.LOW,
				BossAttackDirection.HIGH
		};
		for (int attackId = 1; attackId <= expected.length; attackId++) {
			assertEquals(expected[attackId - 1], SnakeKingAttackRules.directionFor(attackId));
		}
	}

	@Test
	void specialAttacksKeepLongReadableWindups() {
		assertEquals(22, SnakeKingAttackRules.HIGH_POISON_HIT_TICK
				- SnakeKingAttackRules.HIGH_POISON_WARNING_START);
		assertEquals(24, SnakeKingAttackRules.GROUND_POISON_HIT_TICK
				- SnakeKingAttackRules.GROUND_POISON_WARNING_START);
		assertEquals(24, SnakeKingAttackRules.OVERHEAD_HIT_TICK
				- SnakeKingAttackRules.OVERHEAD_WARNING_START);
	}
}
