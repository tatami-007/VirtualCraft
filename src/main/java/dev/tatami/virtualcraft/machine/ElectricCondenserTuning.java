package dev.tatami.virtualcraft.machine;

public final class ElectricCondenserTuning {
	private static final int MAX_PROGRESS = 60;
	private static final int BASE_PROGRESS_PER_TICK = 2;
	private static final int BASE_ENERGY_PER_TICK = 200;

	private ElectricCondenserTuning() {
	}

	public static int maxProgress() {
		return MAX_PROGRESS;
	}

	public static int progressPerTick(int speedCards) {
		return switch (normalizedSpeedCards(speedCards)) {
			case 1 -> 4;
			case 2 -> 8;
			case 3 -> 12;
			default -> BASE_PROGRESS_PER_TICK;
		};
	}

	public static int energyPerTick(int speedCards) {
		return switch (normalizedSpeedCards(speedCards)) {
			case 1 -> 400;
			case 2 -> 750;
			case 3 -> 1200;
			default -> BASE_ENERGY_PER_TICK;
		};
	}

	public static int ticksPerCraft(int speedCards) {
		int progressPerTick = progressPerTick(speedCards);
		return (maxProgress() + progressPerTick - 1) / progressPerTick;
	}

	public static int totalEnergyPerCraft(int speedCards) {
		return ticksPerCraft(speedCards) * energyPerTick(speedCards);
	}

	private static int normalizedSpeedCards(int speedCards) {
		return Math.min(3, Math.max(0, speedCards));
	}
}
