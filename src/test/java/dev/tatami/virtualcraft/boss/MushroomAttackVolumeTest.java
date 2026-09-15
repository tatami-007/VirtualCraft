package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MushroomAttackVolumeTest {
	@Test
	void surgeAdvancesForwardInsteadOfCyclingAcrossLanes() {
		assertEquals(0.35, MushroomAttackVolume.surgeStart(1), 0.0001);
		assertEquals(3.2, MushroomAttackVolume.surgeStart(2), 0.0001);
		assertEquals(6.05, MushroomAttackVolume.surgeStart(3), 0.0001);
		assertTrue(MushroomAttackVolume.surgeLength(2) > 4.0);
	}

	@Test
	void convergenceVariantMovesTheSafeGap() {
		assertEquals(0.0, MushroomAttackVolume.convergenceGapAngle(0), 0.0001);
		assertEquals(315.0, MushroomAttackVolume.convergenceGapAngle(7), 0.0001);
		assertNotEquals(MushroomAttackVolume.convergenceGapAngle(2),
				MushroomAttackVolume.convergenceGapAngle(3));
	}

	@Test
	void highAndRibbonVolumesRemainReadable() {
		assertTrue(MushroomAttackVolume.HIGH_MAX_Y > MushroomAttackVolume.HIGH_MIN_Y);
		assertTrue(MushroomAttackVolume.NEEDLE_LENGTH > 8.0);
		assertTrue(MushroomAttackVolume.BREATH_HALF_WIDTH >= 2.5);
		assertTrue(MushroomAttackVolume.SURGE_HALF_WIDTH >= 1.0);
	}
}
