package dev.tatami.virtualcraft.machine.advanced;

public final class AdvancedMachineLogic {
	public static final int WORK_SCALE = 1000;

	private AdvancedMachineLogic() {
	}

	public static TickResult tick(LockedMachineProcess process, int cpuCount, EnergyAccess energy) {
		long remainingWork = process.totalWorkUnits() - process.workUnits();
		long remainingEnergy = process.totalEnergy() - process.consumedEnergy();
		if (remainingWork <= 0L && remainingEnergy <= 0L) {
			return TickResult.COMPLETE;
		}

		long workThisTick = AdvancedMachineRules.workPerTick(remainingWork, cpuCount);
		long targetWork = process.workUnits() + workThisTick;
		long targetEnergy = AdvancedMachineRules.targetEnergy(
				process.totalEnergy(), targetWork, process.totalWorkUnits());
		long neededEnergy = Math.min(remainingEnergy, Math.max(0L, targetEnergy - process.consumedEnergy()));
		if (neededEnergy > Integer.MAX_VALUE || energy.extract((int) neededEnergy, true) < neededEnergy) {
			process.recordPowerWait();
			return TickResult.WAITING_FOR_POWER;
		}
		if (neededEnergy > 0L && energy.extract((int) neededEnergy, false) < neededEnergy) {
			process.recordPowerWait();
			return TickResult.WAITING_FOR_POWER;
		}
		process.advance(workThisTick, neededEnergy);
		return process.workUnits() >= process.totalWorkUnits()
				&& process.consumedEnergy() >= process.totalEnergy()
						? TickResult.COMPLETE
						: TickResult.RUNNING;
	}

	public enum TickResult {
		RUNNING,
		WAITING_FOR_POWER,
		COMPLETE
	}

	@FunctionalInterface
	public interface EnergyAccess {
		int extract(int amount, boolean simulate);
	}
}
