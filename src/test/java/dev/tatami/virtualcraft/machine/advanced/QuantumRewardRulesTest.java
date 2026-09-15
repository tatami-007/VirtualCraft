package dev.tatami.virtualcraft.machine.advanced;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QuantumRewardRulesTest {
	@Test
	void stablePowerImprovesMaterialAndComponentYield() {
		assertEquals(4, QuantumRewardRules.materialYield(0, 0));
		assertEquals(8, QuantumRewardRules.materialYield(0, 1000));
		assertEquals(12, QuantumRewardRules.materialYield(8, 1000));
		assertEquals(1, QuantumRewardRules.componentYield(0, 500));
		assertEquals(3, QuantumRewardRules.componentYield(6, 1000));
	}

	@Test
	void hashQualityScalesAcrossRackBuilds() {
		assertEquals(QuantumRewardRules.VoucherTier.BASIC,
				QuantumRewardRules.hashTier(2, 0, 0, 1000));
		assertEquals(QuantumRewardRules.VoucherTier.ENCRYPTED,
				QuantumRewardRules.hashTier(4, 3, 4, 1000));
		assertEquals(QuantumRewardRules.VoucherTier.QUANTUM,
				QuantumRewardRules.hashTier(4, 6, 8, 1000));
		assertEquals(QuantumRewardRules.VoucherTier.GENESIS,
				QuantumRewardRules.hashTier(8, 10, 8, 1000));
	}

	@Test
	void powerInterruptionsReduceStability() {
		assertEquals(1000, QuantumRewardRules.stabilityPermille(200, 0));
		assertEquals(800, QuantumRewardRules.stabilityPermille(160, 40));
		assertEquals(0, QuantumRewardRules.stabilityPermille(0, 40));
	}
}
