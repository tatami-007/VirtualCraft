package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BossDamageRulesTest {
	@Test
	void capsSingleHitAndRollingDamage() {
		assertEquals(11.2F, BossDamageRules.acceptedDamage(90.0F, 28.0F, 72.0F, 0.0F,
				2.0, 32.0, false));
		assertEquals(4.0F, BossDamageRules.acceptedDamage(20.0F, 28.0F, 72.0F, 68.0F,
				2.0, 32.0, false));
	}

	@Test
	void fadesLongRangeDamageAndPreservesStaggerVulnerability() {
		assertEquals(4.0F, BossDamageRules.acceptedDamage(20.0F, 28.0F, 72.0F, 0.0F,
				40.0, 32.0, false), 0.001F);
		assertEquals(30.0F, BossDamageRules.acceptedDamage(20.0F, 28.0F, 72.0F, 0.0F,
				2.0, 32.0, true), 0.001F);
		assertEquals(0.0F, BossDamageRules.acceptedDamage(20.0F, 28.0F, 72.0F, 0.0F,
				48.0, 32.0, false));
	}

	@Test
	void damageBucketDecaysOverOneSecond() {
		assertEquals(68.4F, BossDamageRules.decayedBucket(72.0F, 72.0F), 0.001F);
	}
}
