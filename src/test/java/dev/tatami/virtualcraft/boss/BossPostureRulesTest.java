package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BossPostureRulesTest {
	@Test
	void weakOrUnchargedMeleeDoesNotDrainPosture() {
		assertEquals(0.0F, BossPostureRules.chargedMeleePressure(20.0F, 0.84F));
		assertEquals(0.0F, BossPostureRules.chargedMeleePressure(0.0F, 1.0F));
	}

	@Test
	void chargedMeleePressureIsSlowAndFractional() {
		assertEquals(0.35F, BossPostureRules.chargedMeleePressure(1.0F, 0.85F));
		assertEquals(0.5F, BossPostureRules.chargedMeleePressure(8.0F, 1.0F));
		assertEquals(0.75F, BossPostureRules.chargedMeleePressure(40.0F, 1.0F));
	}

	@Test
	void parryAndJumpStrikeUseFixedSekiroValues() {
		assertEquals(8.0F, BossPostureRules.preciseParryDamage());
		assertEquals(3.0F, BossPostureRules.jumpStrikeDamage());
		assertEquals(4.0F, BossPostureRules.HIGH_WHIFF_POSTURE_DAMAGE);
	}

	@Test
	void preciseParryUsesFirstFifteenShieldTicks() {
		assertEquals(100, BossPostureRules.SHIELD_BREAK_TICKS);
		assertEquals(true, BossPostureRules.isPreciseParry(true, 0));
		assertEquals(true, BossPostureRules.isPreciseParry(true, 14));
		assertEquals(false, BossPostureRules.isPreciseParry(true, 15));
		assertEquals(false, BossPostureRules.isPreciseParry(false, 5));
	}

	@Test
	void lowDodgeRequiresARealJumpInsteadOfAnyAirborneState() {
		assertEquals(true, BossPostureRules.isNormalJumpStart(false, false, false, false, 0.42, 0.0F));
		assertEquals(false, BossPostureRules.isNormalJumpStart(false, false, false, false, -0.2, 2.0F));
		assertEquals(false, BossPostureRules.isNormalJumpStart(false, true, false, false, 0.42, 0.0F));
		assertEquals(false, BossPostureRules.isNormalJumpStart(false, false, true, false, 0.42, 0.0F));
		assertEquals(false, BossPostureRules.isNormalJumpStart(false, false, false, true, 0.42, 0.0F));
	}

	@Test
	void lowDodgeAllowsAirborneAndShortLandingGrace() {
		assertEquals(true, BossPostureRules.isLowDodgeWindow(false, false, true, false));
		assertEquals(true, BossPostureRules.isLowDodgeWindow(true, false, false, true));
		assertEquals(false, BossPostureRules.isLowDodgeWindow(false, false, false, false));
		assertEquals(false, BossPostureRules.isLowDodgeWindow(false, true, true, true));
	}
}
