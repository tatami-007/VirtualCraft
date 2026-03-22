package dev.tatami.virtualcraft.blockentity;

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
		assertEquals(3, ElectricCondenserTuning.progressPerTick(1));
		assertEquals(4, ElectricCondenserTuning.progressPerTick(2));
		assertEquals(5, ElectricCondenserTuning.progressPerTick(3));

		assertEquals(20, ElectricCondenserTuning.ticksPerCraft(1));
		assertEquals(15, ElectricCondenserTuning.ticksPerCraft(2));
		assertEquals(12, ElectricCondenserTuning.ticksPerCraft(3));

		assertEquals(200, ElectricCondenserTuning.energyPerTick(0));
		assertEquals(300, ElectricCondenserTuning.energyPerTick(1));
		assertEquals(400, ElectricCondenserTuning.energyPerTick(2));
		assertEquals(500, ElectricCondenserTuning.energyPerTick(3));

		assertEquals(6000, ElectricCondenserTuning.totalEnergyPerCraft(0));
		assertEquals(6000, ElectricCondenserTuning.totalEnergyPerCraft(1));
		assertEquals(6000, ElectricCondenserTuning.totalEnergyPerCraft(2));
		assertEquals(6000, ElectricCondenserTuning.totalEnergyPerCraft(3));
	}
}
