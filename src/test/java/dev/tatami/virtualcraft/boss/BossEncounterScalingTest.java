package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BossEncounterScalingTest {
	@Test
	void addsCapacityForMultiplayerWithoutUnlimitedScaling() {
		assertEquals(1.0, BossEncounterScaling.healthScale(0), 0.0001);
		assertEquals(1.0, BossEncounterScaling.healthScale(1), 0.0001);
		assertEquals(1.45, BossEncounterScaling.healthScale(2), 0.0001);
		assertEquals(1.9, BossEncounterScaling.healthScale(3), 0.0001);
		assertEquals(2.35, BossEncounterScaling.healthScale(4), 0.0001);
		assertEquals(2.35, BossEncounterScaling.healthScale(12), 0.0001);
	}
}
