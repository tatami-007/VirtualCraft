package dev.tatami.virtualcraft.blockentity;

final class ElectricCondenserTuning {
	private static final int MAX_PROGRESS = 60;
	private static final int BASE_PROGRESS_PER_TICK = 2;
	private static final int BASE_ENERGY_PER_TICK = 200;
	private static final int SPEED_CARD_ENERGY_PER_TICK = 100;

	private ElectricCondenserTuning() {
	}

	static int maxProgress() {
		return MAX_PROGRESS;
	}

	static int progressPerTick(int speedCards) {
		return BASE_PROGRESS_PER_TICK + Math.max(0, speedCards);
	}

	static int energyPerTick(int speedCards) {
		return BASE_ENERGY_PER_TICK + Math.max(0, speedCards) * SPEED_CARD_ENERGY_PER_TICK;
	}

	static int ticksPerCraft(int speedCards) {
		return maxProgress() / progressPerTick(speedCards);
	}

	static int totalEnergyPerCraft(int speedCards) {
		return ticksPerCraft(speedCards) * energyPerTick(speedCards);
	}
}
