package dev.tatami.virtualcraft.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataMineRewardsTest {

	@Test
	void emptyPouchSettlesToNothing() {
		assertEquals(0, DataMineRewards.fragments(0, 10));
		assertEquals(0, DataMineRewards.voucherRolls(0, 10));
	}

	@Test
	void fragmentsGrowWithDepthForSameOre() {
		int shallow = DataMineRewards.fragments(10, 1);
		int deep = DataMineRewards.fragments(10, 9);
		assertTrue(deep > shallow, "deeper runs should settle more fragments");
	}

	@Test
	void fragmentsGrowWithOreForSameDepth() {
		int small = DataMineRewards.fragments(5, 5);
		int large = DataMineRewards.fragments(20, 5);
		assertTrue(large > small);
	}

	@Test
	void voucherChancesAreBounded() {
		for (int tier = 0; tier < DataMineRewards.VOUCHER_TIERS; tier++) {
			for (int depth = 1; depth <= 50; depth++) {
				float chance = DataMineRewards.voucherChance(tier, depth);
				assertTrue(chance >= 0f && chance <= 0.95f, "chance out of range: " + chance);
			}
		}
	}

	@Test
	void higherTiersAreRarerThanLowerAtSameDepth() {
		int depth = 8;
		for (int tier = 1; tier < DataMineRewards.VOUCHER_TIERS; tier++) {
			assertTrue(DataMineRewards.voucherChance(tier, depth) < DataMineRewards.voucherChance(tier - 1, depth),
					"tier " + tier + " should be rarer than " + (tier - 1));
		}
	}

	@Test
	void deeperRunsRaiseEveryTierChance() {
		for (int tier = 0; tier < DataMineRewards.VOUCHER_TIERS; tier++) {
			assertTrue(DataMineRewards.voucherChance(tier, 20) >= DataMineRewards.voucherChance(tier, 1),
					"deeper should not lower tier " + tier);
		}
	}

	@Test
	void outOfRangeTierGivesZero() {
		assertEquals(0f, DataMineRewards.voucherChance(-1, 5));
		assertEquals(0f, DataMineRewards.voucherChance(DataMineRewards.VOUCHER_TIERS, 5));
	}

	@Test
	void milestoneBonusKicksInEveryFiveLevels() {
		assertEquals(0, DataMineRewards.milestoneBonus(4));
		assertEquals(25, DataMineRewards.milestoneBonus(5));
		assertEquals(50, DataMineRewards.milestoneBonus(10));
	}

	@Test
	void voucherRollsGrowWithOreAndDepth() {
		assertTrue(DataMineRewards.voucherRolls(30, 10) > DataMineRewards.voucherRolls(3, 1));
	}
}
