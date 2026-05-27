package dev.tatami.virtualcraft.gameplay;

import java.util.UUID;

final class VirtualDataSectorMath {
	static final int SECTOR_SIZE = 512;
	static final int SECTOR_GRID = 1024;
	static final int FLOOR_Y = 64;

	private VirtualDataSectorMath() {
	}

	static SectorOrigin originFor(UUID playerId) {
		long mixed = mix(playerId.getMostSignificantBits() ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17));
		int gridX = Math.floorMod((int) mixed, SECTOR_GRID) - SECTOR_GRID / 2;
		int gridZ = Math.floorMod((int) (mixed >>> 32), SECTOR_GRID) - SECTOR_GRID / 2;
		if (gridX == 0 && gridZ == 0) {
			gridX = 1;
		}
		return new SectorOrigin(gridX * SECTOR_SIZE, FLOOR_Y, gridZ * SECTOR_SIZE);
	}

	private static long mix(long value) {
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}

	record SectorOrigin(int x, int y, int z) {
	}
}
