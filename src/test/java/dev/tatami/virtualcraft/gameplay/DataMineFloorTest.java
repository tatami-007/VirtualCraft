package dev.tatami.virtualcraft.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

class DataMineFloorTest {

	@Test
	void rejectsNonPositiveDepth() {
		try {
			DataMineFloor.generate(1L, 0);
			throw new AssertionError("expected IllegalArgumentException for depth 0");
		} catch (IllegalArgumentException expected) {
			// ok
		}
	}

	@Test
	void layoutIsStableForSameSeedAndDepth() {
		DataMineFloor a = DataMineFloor.generate(12345L, 3);
		DataMineFloor b = DataMineFloor.generate(12345L, 3);
		for (int x = 0; x < DataMineFloor.WIDTH; x++) {
			for (int z = 0; z < DataMineFloor.DEPTH; z++) {
				assertEquals(a.isOpen(x, z), b.isOpen(x, z), "open mask differs at " + x + "," + z);
			}
		}
		assertEquals(a.oreVeins().size(), b.oreVeins().size());
		assertEquals(a.mobSpawns().size(), b.mobSpawns().size());
	}

	@Test
	void entryShaftAndVeinsAreOpen() {
		DataMineFloor floor = DataMineFloor.generate(7L, 4);
		assertTrue(floor.isOpen(floor.entryX(), floor.entryZ()), "entry must be open");
		assertTrue(floor.isOpen(floor.shaftX(), floor.shaftZ()), "shaft must be open");
		assertTrue(floor.isOpen(floor.padX(), floor.padZ()), "pad must be open");
		for (int[] vein : floor.oreVeins()) {
			assertTrue(floor.isOpen(vein[0], vein[1]), "vein center must be open");
		}
	}

	@Test
	void shaftIsNotEntry() {
		DataMineFloor floor = DataMineFloor.generate(7L, 1);
		assertFalse(floor.entryX() == floor.shaftX() && floor.entryZ() == floor.shaftZ());
	}

	@Test
	void everyKeyPointIsReachableFromEntry() {
		for (int depth = 1; depth <= 12; depth++) {
			DataMineFloor floor = DataMineFloor.generate(99L + depth, depth);
			boolean[][] reached = bfs(floor);
			assertTrue(reached[floor.shaftX()][floor.shaftZ()], "shaft unreachable at depth " + depth);
			for (int[] vein : floor.oreVeins()) {
				assertTrue(reached[vein[0]][vein[1]], "vein unreachable at depth " + depth);
			}
		}
	}

	@Test
	void oreAndMobCountsIncreaseWithDepth() {
		int prevOre = -1;
		int prevMob = -1;
		for (int depth = 1; depth <= 10; depth++) {
			DataMineFloor floor = DataMineFloor.generate(500L, depth);
			int ore = floor.oreVeins().size();
			int mob = floor.mobSpawns().size();
			assertTrue(ore > prevOre, "ore count should grow with depth");
			assertTrue(mob > prevMob, "mob count should grow with depth");
			prevOre = ore;
			prevMob = mob;
		}
	}

	@Test
	void mobsNeverSpawnInEntrySafeZone() {
		DataMineFloor floor = DataMineFloor.generate(321L, 8);
		for (int[] mob : floor.mobSpawns()) {
			int dist = Math.abs(mob[0] - floor.entryX()) + Math.abs(mob[1] - floor.entryZ());
			assertTrue(dist >= 8, "mob too close to entry: dist=" + dist);
			assertTrue(floor.isOpen(mob[0], mob[1]), "mob spawn must be open");
		}
	}

	@Test
	void differentSeedsDiverge() {
		DataMineFloor a = DataMineFloor.generate(1L, 5);
		DataMineFloor b = DataMineFloor.generate(2L, 5);
		boolean anyDiff = false;
		for (int x = 0; x < DataMineFloor.WIDTH && !anyDiff; x++) {
			for (int z = 0; z < DataMineFloor.DEPTH; z++) {
				if (a.isOpen(x, z) != b.isOpen(x, z)) {
					anyDiff = true;
					break;
				}
			}
		}
		assertTrue(anyDiff, "different seeds should usually diverge");
	}

	private static boolean[][] bfs(DataMineFloor floor) {
		boolean[][] reached = new boolean[DataMineFloor.WIDTH][DataMineFloor.DEPTH];
		Deque<int[]> queue = new ArrayDeque<>();
		reached[floor.entryX()][floor.entryZ()] = true;
		queue.add(new int[] { floor.entryX(), floor.entryZ() });
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		while (!queue.isEmpty()) {
			int[] cur = queue.poll();
			for (int[] dir : dirs) {
				int nx = cur[0] + dir[0];
				int nz = cur[1] + dir[1];
				if (nx < 0 || nz < 0 || nx >= DataMineFloor.WIDTH || nz >= DataMineFloor.DEPTH) {
					continue;
				}
				if (reached[nx][nz] || !floor.isOpen(nx, nz)) {
					continue;
				}
				reached[nx][nz] = true;
				queue.add(new int[] { nx, nz });
			}
		}
		return reached;
	}
}
