package dev.tatami.virtualcraft.machine.advanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdvancedMachineRulesTest {
	@Test
	void cpuShortensRuntimeWithoutChangingTotalEnergy() {
		long totalWork = 1_200_000L;
		long baseWork = AdvancedMachineRules.workPerTick(totalWork, 0);
		long acceleratedWork = AdvancedMachineRules.workPerTick(totalWork, 8);

		assertTrue(acceleratedWork > baseWork);
		assertEquals(400_000L, AdvancedMachineRules.targetEnergy(400_000L, totalWork, totalWork));
		assertEquals(200_000L, AdvancedMachineRules.targetEnergy(400_000L, totalWork / 2, totalWork));
	}

	@Test
	void cpuContributionIsBounded() {
		assertEquals(1_000L, AdvancedMachineRules.workPerTick(10_000L, 0));
		assertEquals(2_000L, AdvancedMachineRules.workPerTick(10_000L, 8));
		assertEquals(2_000L, AdvancedMachineRules.workPerTick(10_000L, 80));
	}

	@Test
	void overclockUsesOnePointFiveSpeedAndOnePointSevenFiveEnergy() {
		assertEquals(350, AdvancedMachineRules.overclockEnergyCost(200));
		assertEquals(6, AdvancedMachineRules.overclockProgress(4, 0));
		assertEquals(6, AdvancedMachineRules.overclockProgress(4, 1));
		assertEquals(4, AdvancedMachineRules.overclockProgress(3, 0));
		assertEquals(5, AdvancedMachineRules.overclockProgress(3, 1));
	}
}
