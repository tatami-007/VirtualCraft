package dev.tatami.virtualcraft.gameplay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VirtualDataIntrusionPatternTest {
	@Test
	void patternIsStableForSeed() {
		VirtualDataIntrusionPattern first = VirtualDataIntrusionPattern.create(123456789L);
		VirtualDataIntrusionPattern second = VirtualDataIntrusionPattern.create(123456789L);

		assertArrayEquals(first.calibrationNodes(), second.calibrationNodes());
		assertArrayEquals(first.routeNodes(), second.routeNodes());
	}

	@Test
	void patternUsesValidNodeRanges() {
		VirtualDataIntrusionPattern pattern = VirtualDataIntrusionPattern.create(42L);

		assertEquals(VirtualDataIntrusionPattern.CALIBRATION_COUNT, pattern.calibrationNodes().length);
		assertTrue(pattern.routeNodes().length >= VirtualDataIntrusionPattern.MIN_ROUTE_COUNT);
		assertTrue(pattern.routeNodes().length <= VirtualDataIntrusionPattern.MAX_ROUTE_COUNT);
		for (int node : pattern.calibrationNodes()) {
			assertTrue(node >= 0 && node < VirtualDataIntrusionPattern.NODE_COUNT);
		}
		for (int node : pattern.routeNodes()) {
			assertTrue(node >= 0 && node < VirtualDataIntrusionPattern.NODE_COUNT);
		}
	}

	@Test
	void calibrationNodesAreUnique() {
		VirtualDataIntrusionPattern pattern = VirtualDataIntrusionPattern.create(98765L);
		boolean[] seen = new boolean[VirtualDataIntrusionPattern.NODE_COUNT];

		for (int node : pattern.calibrationNodes()) {
			assertFalse(seen[node]);
			seen[node] = true;
		}
	}

	@Test
	void accessorsDoNotExposeInternalArrays() {
		VirtualDataIntrusionPattern pattern = VirtualDataIntrusionPattern.create(123L);

		pattern.calibrationNodes()[0] = -1;
		pattern.routeNodes()[0] = -1;

		assertTrue(pattern.calibrationNodes()[0] >= 0);
		assertTrue(pattern.routeNodes()[0] >= 0);
	}

	@Test
	void rewardMultiplierRewardsCleanFastRuns() {
		assertEquals(4, VirtualDataIntrusionPattern.rewardMultiplier(0, 600, 1500));
		assertEquals(2, VirtualDataIntrusionPattern.rewardMultiplier(1, 0, 1500));
		assertEquals(1, VirtualDataIntrusionPattern.rewardMultiplier(2, 0, 1500));
	}
}
