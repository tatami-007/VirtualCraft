package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnakeDashRulesTest {
	@Test
	void bothPassesHaveEightAuthoredMovementTicks() {
		assertTrue(SnakeDashRules.isActive(32));
		assertTrue(SnakeDashRules.isActive(39));
		assertFalse(SnakeDashRules.isActive(40));
		assertTrue(SnakeDashRules.isActive(56));
		assertTrue(SnakeDashRules.isActive(63));
		assertFalse(SnakeDashRules.isActive(64));
	}

	@Test
	void speedReachesLockedTargetWithoutUnboundedTeleporting() {
		assertEquals(0.55, SnakeDashRules.speedForDistance(3.0), 1.0E-6);
		assertEquals(1.08125, SnakeDashRules.speedForDistance(10.0), 1.0E-6);
		assertEquals(2.08125, SnakeDashRules.speedForDistance(18.0), 1.0E-6);
		assertEquals(2.15, SnakeDashRules.speedForDistance(40.0), 1.0E-6);
	}
}
