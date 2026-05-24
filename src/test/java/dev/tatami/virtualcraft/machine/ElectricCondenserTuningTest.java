package dev.tatami.virtualcraft.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ElectricCondenserTuningTest {
	@Test
	void baseMachineFinishesInThirtyTicks() {
		assertEquals(60, ElectricCondenserTuning.maxProgress());
		assertEquals(2, ElectricCondenserTuning.progressPerTick(0));
		assertEquals(30, ElectricCondenserTuning.ticksPerCraft(0));
	}

	@Test
	void speedCardsIncreaseThroughputAndPreserveSixThousandTotalEnergy() {
		assertEquals(4, ElectricCondenserTuning.progressPerTick(1));
		assertEquals(8, ElectricCondenserTuning.progressPerTick(2));
		assertEquals(12, ElectricCondenserTuning.progressPerTick(3));

		assertEquals(15, ElectricCondenserTuning.ticksPerCraft(1));
		assertEquals(8, ElectricCondenserTuning.ticksPerCraft(2));
		assertEquals(5, ElectricCondenserTuning.ticksPerCraft(3));

		assertEquals(200, ElectricCondenserTuning.energyPerTick(0));
		assertEquals(400, ElectricCondenserTuning.energyPerTick(1));
		assertEquals(750, ElectricCondenserTuning.energyPerTick(2));
		assertEquals(1200, ElectricCondenserTuning.energyPerTick(3));

		assertEquals(6000, ElectricCondenserTuning.totalEnergyPerCraft(0));
		assertEquals(6000, ElectricCondenserTuning.totalEnergyPerCraft(1));
		assertEquals(6000, ElectricCondenserTuning.totalEnergyPerCraft(2));
		assertEquals(6000, ElectricCondenserTuning.totalEnergyPerCraft(3));
	}
}
