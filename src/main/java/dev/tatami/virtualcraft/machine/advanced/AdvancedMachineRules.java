package dev.tatami.virtualcraft.machine.advanced;

public final class AdvancedMachineRules {
	private AdvancedMachineRules() {
	}

	public static long workPerTick(long remainingWork, int cpuCount) {
		int boundedCpu = Math.min(8, Math.max(0, cpuCount));
		return Math.min(Math.max(0L, remainingWork),
				AdvancedMachineLogic.WORK_SCALE + boundedCpu * 125L);
	}

	public static long targetEnergy(long totalEnergy, long targetWork, long totalWork) {
		if (totalEnergy <= 0L || targetWork <= 0L) {
			return 0L;
		}
		long divisor = Math.max(1L, totalWork);
		return (totalEnergy * targetWork + divisor - 1L) / divisor;
	}

	public static int overclockEnergyCost(int baseCost) {
		return Math.max(1, (baseCost * 7 + 3) / 4);
	}

	public static int overclockProgress(int baseProgress, long gameTime) {
		int half = (gameTime & 1L) == 0L ? baseProgress / 2 : (baseProgress + 1) / 2;
		return baseProgress + half;
	}
}
