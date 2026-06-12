package dev.tatami.virtualcraft.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PowMinerOutputRulesTest {
	@Test
	void legacySingleSlotInventoriesUpgradeToDualOutput() {
		assertEquals(2, PowMinerOutputRules.normalizedSlotCount(1));
	}

	@Test
	void newerInventoriesAreNotShrunk() {
		assertEquals(3, PowMinerOutputRules.normalizedSlotCount(3));
	}
}
