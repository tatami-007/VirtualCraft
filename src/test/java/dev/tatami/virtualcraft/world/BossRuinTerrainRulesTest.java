package dev.tatami.virtualcraft.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossRuinTerrainRulesTest {
	@Test void acceptsRollingDryGroundAndUsesMedianWithoutMutatingInput() {
		int[] heights = {80, 76, 83, 78, 82};
		assertEquals(80, BossRuinTerrainRules.floorHeight(heights, 63).orElseThrow());
		assertArrayEquals(new int[]{80, 76, 83, 78, 82}, heights);
	}
	@Test void rejectsCliffsWaterAndMissingSamples() {
		assertTrue(BossRuinTerrainRules.floorHeight(new int[]{70, 72, 110}, 63).isEmpty());
		assertTrue(BossRuinTerrainRules.floorHeight(new int[]{63, 70, 73}, 63).isEmpty());
		assertTrue(BossRuinTerrainRules.floorHeight(new int[0], 63).isEmpty());
	}
	@Test void rejectsDeepCutEvenWhenOverallReliefPasses() {
		assertTrue(BossRuinTerrainRules.floorHeight(new int[]{80,80,80,80,95}, 63).isEmpty());
		assertTrue(BossRuinTerrainRules.floorHeight(new int[]{80,95,95,95,95}, 63).isEmpty());
	}
}
