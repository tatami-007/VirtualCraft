package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnakeAttackVolumeTest {
	@Test
	void coneUsesLockedDirectionHeightAndAngle() {
		SnakeAttackVolume cone = SnakeAttackVolume.cone(0.0, 0.0, 0.0,
				0.0, 1.0, 6.0, Math.toRadians(45.0), -0.5, 2.5);
		assertTrue(cone.contains(0.0, 1.0, 4.0));
		assertTrue(cone.contains(2.0, 1.0, 4.0));
		assertFalse(cone.contains(4.0, 1.0, 2.0));
		assertFalse(cone.contains(0.0, 3.0, 4.0));
	}

	@Test
	void beamAndRingMatchTheirRenderedFootprints() {
		SnakeAttackVolume beam = SnakeAttackVolume.beam(0.0, 0.0, 0.0,
				1.0, 0.0, 12.0, 1.5, 0.0, 3.0);
		assertTrue(beam.contains(8.0, 1.0, 1.4));
		assertFalse(beam.contains(8.0, 1.0, 1.6));

		SnakeAttackVolume ring = SnakeAttackVolume.ring(0.0, 0.0, 0.0,
				6.0, 1.0, -0.5, 2.0);
		assertTrue(ring.contains(5.5, 1.0, 0.0));
		assertFalse(ring.contains(0.5, 1.0, 0.0));
	}
}
