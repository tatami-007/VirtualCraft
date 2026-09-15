package dev.tatami.virtualcraft.machine.advanced;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ComputeProfileTest {
	@Test
	void gpuAddsParallelLanesUpToThree() {
		assertEquals(1, new ComputeProfile(0, 0, 0, 1).parallelLanes());
		assertEquals(2, new ComputeProfile(0, 2, 0, 1).parallelLanes());
		assertEquals(3, new ComputeProfile(0, 4, 0, 2).parallelLanes());
		assertEquals(3, new ComputeProfile(0, 12, 0, 6).parallelLanes());
	}

	@Test
	void memoryAddsRangeWithBoundedGrowth() {
		assertEquals(0, new ComputeProfile(0, 0, 0, 1).additionalRangeChunks());
		assertEquals(4, new ComputeProfile(0, 0, 2, 1).additionalRangeChunks());
		assertEquals(8, new ComputeProfile(0, 0, 8, 2).additionalRangeChunks());
	}

	@Test
	void offlineProfilesContributeNoModules() {
		assertEquals(ComputeProfile.EMPTY,
				ComputeProfile.EMPTY.add(new ComputeProfile(0, 0, 0, 0)));
	}
}
