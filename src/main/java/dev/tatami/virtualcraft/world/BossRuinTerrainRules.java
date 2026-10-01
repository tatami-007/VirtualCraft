package dev.tatami.virtualcraft.world;

import java.util.Arrays;
import java.util.OptionalInt;

/** Pure siting rules shared by both monumental ruins. Heights are first air Y. */
public final class BossRuinTerrainRules {
	public static final int MAX_RELIEF = 18;
	public static final int MAX_FOUNDATION_DEPTH = 24;

	private BossRuinTerrainRules() {}

	public static OptionalInt floorHeight(int[] samples, int seaLevel) {
		if (samples.length == 0) return OptionalInt.empty();
		int[] sorted = samples.clone();
		Arrays.sort(sorted);
		int floor = sorted[sorted.length / 2];
		if (sorted[0] <= seaLevel + 1
				|| sorted[sorted.length - 1] - sorted[0] > MAX_RELIEF
				|| sorted[sorted.length - 1] - floor > 10
				|| floor - sorted[0] > 12) return OptionalInt.empty();
		return OptionalInt.of(floor);
	}
}
