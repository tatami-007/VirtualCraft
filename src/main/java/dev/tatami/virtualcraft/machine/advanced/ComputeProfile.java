package dev.tatami.virtualcraft.machine.advanced;

public record ComputeProfile(int cpuCount, int gpuCount, int memoryCount, int onlineRacks) {
	public static final ComputeProfile EMPTY = new ComputeProfile(0, 0, 0, 0);

	public ComputeProfile add(ComputeProfile other) {
		return new ComputeProfile(
				cpuCount + other.cpuCount,
				gpuCount + other.gpuCount,
				memoryCount + other.memoryCount,
				onlineRacks + other.onlineRacks);
	}

	public int computeScore() {
		return cpuCount + gpuCount;
	}

	public int parallelLanes() {
		return Math.min(3, 1 + gpuCount / 2);
	}

	public int additionalRangeChunks() {
		return Math.min(8, memoryCount * 2);
	}
}
