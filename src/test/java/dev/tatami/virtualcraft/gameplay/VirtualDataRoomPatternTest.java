package dev.tatami.virtualcraft.gameplay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.tatami.virtualcraft.gameplay.VirtualDataRoomPattern.Target;
import dev.tatami.virtualcraft.gameplay.VirtualDataRoomPattern.TargetType;

class VirtualDataRoomPatternTest {
	@Test
	void patternIsStableForSeed() {
		VirtualDataRoomPattern first = VirtualDataRoomPattern.create(123456789L);
		VirtualDataRoomPattern second = VirtualDataRoomPattern.create(123456789L);

		assertArrayEquals(first.scanRelays(), second.scanRelays());
		assertArrayEquals(first.routeTargets(), second.routeTargets());
	}

	@Test
	void generatedTargetsUseValidRanges() {
		VirtualDataRoomPattern pattern = VirtualDataRoomPattern.create(42L);

		assertEquals(VirtualDataRoomPattern.SCAN_COUNT, pattern.scanRelays().length);
		assertTrue(pattern.routeTargets().length >= VirtualDataRoomPattern.MIN_ROUTE_COUNT);
		assertTrue(pattern.routeTargets().length <= VirtualDataRoomPattern.MAX_ROUTE_COUNT);
		for (int relay : pattern.scanRelays()) {
			assertTrue(relay >= 0 && relay < VirtualDataRoomPattern.RELAY_COUNT);
		}
		for (Target target : pattern.routeTargets()) {
			int bound = target.type() == TargetType.RELAY ? VirtualDataRoomPattern.RELAY_COUNT : VirtualDataIntrusionPattern.NODE_COUNT;
			assertTrue(target.index() >= 0 && target.index() < bound);
		}
	}

	@Test
	void roomNodeOffsetsDoNotOverlap() {
		Set<String> occupied = new HashSet<>();

		for (int index = 0; index < VirtualDataRoomPattern.RELAY_COUNT; index++) {
			assertTrue(occupied.add(key(VirtualDataRoomPattern.relayOffset(index))));
		}
		for (int index = 0; index < VirtualDataRoomPattern.CACHE_COUNT; index++) {
			assertTrue(occupied.add(key(VirtualDataRoomPattern.cacheOffset(index))));
		}
		for (int index = 0; index < VirtualDataRoomPattern.CORRUPTED_COUNT; index++) {
			assertTrue(occupied.add(key(VirtualDataRoomPattern.corruptedOffset(index))));
		}
		for (int index = 0; index < VirtualDataRoomPattern.GATE_COUNT; index++) {
			assertTrue(occupied.add(key(VirtualDataRoomPattern.gateOffset(index))));
		}
	}

	@Test
	void nearestHelpersResolveExactOffsets() {
		for (int index = 0; index < VirtualDataRoomPattern.RELAY_COUNT; index++) {
			VirtualDataRoomPattern.Offset offset = VirtualDataRoomPattern.relayOffset(index);
			assertEquals(index, VirtualDataRoomPattern.nearestRelayIndex(offset.dx(), offset.dy(), offset.dz()));
		}
		for (int index = 0; index < VirtualDataRoomPattern.CACHE_COUNT; index++) {
			VirtualDataRoomPattern.Offset offset = VirtualDataRoomPattern.cacheOffset(index);
			assertEquals(index, VirtualDataRoomPattern.nearestCacheIndex(offset.dx(), offset.dy(), offset.dz()));
		}
	}

	@Test
	void rewardMultiplierUsesCachesAndPerformance() {
		assertEquals(6, VirtualDataRoomPattern.rewardMultiplier(0, 1_000, 2_400, 2));
		assertNotEquals(
				VirtualDataRoomPattern.rewardMultiplier(3, 0, 2_400, 0),
				VirtualDataRoomPattern.rewardMultiplier(0, 1_000, 2_400, 2));
		assertEquals(1, VirtualDataRoomPattern.rewardMultiplier(4, 0, 2_400, 0));
	}

	private static String key(VirtualDataRoomPattern.Offset offset) {
		return offset.dx() + "," + offset.dy() + "," + offset.dz();
	}
}
