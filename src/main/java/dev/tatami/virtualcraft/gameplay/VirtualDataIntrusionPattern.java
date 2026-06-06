package dev.tatami.virtualcraft.gameplay;

import java.util.Arrays;

public record VirtualDataIntrusionPattern(int[] calibrationNodes, int[] routeNodes) {
	public static final int NODE_COUNT = 8;
	public static final int CALIBRATION_COUNT = 3;
	public static final int MIN_ROUTE_COUNT = 4;
	public static final int MAX_ROUTE_COUNT = 5;

	public VirtualDataIntrusionPattern {
		calibrationNodes = calibrationNodes == null ? new int[0] : calibrationNodes.clone();
		routeNodes = routeNodes == null ? new int[0] : routeNodes.clone();
	}

	@Override
	public int[] calibrationNodes() {
		return calibrationNodes.clone();
	}

	@Override
	public int[] routeNodes() {
		return routeNodes.clone();
	}

	public boolean containsCalibrationNode(int node) {
		return Arrays.stream(calibrationNodes).anyMatch(value -> value == node);
	}

	public static VirtualDataIntrusionPattern create(long seed) {
		long cursor = mix(seed);
		int[] calibration = uniqueNodes(cursor, CALIBRATION_COUNT);
		cursor = mix(cursor + 0x9E3779B97F4A7C15L);
		int routeCount = MIN_ROUTE_COUNT + (int) Math.floorMod(cursor, MAX_ROUTE_COUNT - MIN_ROUTE_COUNT + 1);
		int[] route = new int[routeCount];
		int previous = -1;
		for (int index = 0; index < route.length; index++) {
			cursor = mix(cursor + index * 31L);
			int node = Math.floorMod((int) cursor, NODE_COUNT);
			if (node == previous) {
				node = (node + 2 + index) % NODE_COUNT;
			}
			route[index] = node;
			previous = node;
		}
		return new VirtualDataIntrusionPattern(calibration, route);
	}

	public static int rewardMultiplier(int corrosion, long remainingTicks, long totalTicks) {
		int multiplier = 1;
		if (corrosion <= 1) {
			multiplier++;
		}
		if (corrosion == 0) {
			multiplier++;
		}
		if (totalTicks > 0L && remainingTicks * 3L >= totalTicks) {
			multiplier++;
		}
		return Math.max(1, Math.min(4, multiplier));
	}

	private static int[] uniqueNodes(long seed, int count) {
		int[] result = new int[count];
		boolean[] used = new boolean[NODE_COUNT];
		long cursor = seed;
		for (int index = 0; index < count; index++) {
			cursor = mix(cursor + index);
			int node = Math.floorMod((int) cursor, NODE_COUNT);
			while (used[node]) {
				node = (node + 1) % NODE_COUNT;
			}
			used[node] = true;
			result[index] = node;
		}
		return result;
	}

	public static long mix(long value) {
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}
}
