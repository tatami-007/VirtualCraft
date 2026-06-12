package dev.tatami.virtualcraft.gameplay;

import java.util.Arrays;

public record VirtualDataRoomPattern(int[] scanRelays, Target[] routeTargets) {
	public static final int ROOM_VERSION = 2;
	public static final int RELAY_COUNT = 6;
	public static final int CACHE_COUNT = 2;
	public static final int CORRUPTED_COUNT = 4;
	public static final int GATE_COUNT = 3;
	public static final int SCAN_COUNT = 2;
	public static final int MIN_ROUTE_COUNT = 3;
	public static final int MAX_ROUTE_COUNT = 4;

	private static final Offset[] RELAYS = {
			new Offset(-4, 1, -3),
			new Offset(4, 1, -3),
			new Offset(-4, 1, 0),
			new Offset(4, 1, 0),
			new Offset(-4, 1, 3),
			new Offset(4, 1, 3)
	};
	private static final Offset[] CACHES = {
			new Offset(-5, 1, 5),
			new Offset(5, 1, 5)
	};
	private static final Offset[] CORRUPTED = {
			new Offset(-2, 1, -5),
			new Offset(2, 1, -5),
			new Offset(-5, 1, -1),
			new Offset(5, 1, -1)
	};
	private static final Offset[] GATES = {
			new Offset(-5, 1, 4),
			new Offset(5, 1, 4),
			new Offset(0, 1, -5)
	};

	public VirtualDataRoomPattern {
		scanRelays = scanRelays == null ? new int[0] : scanRelays.clone();
		routeTargets = routeTargets == null ? new Target[0] : routeTargets.clone();
	}

	@Override
	public int[] scanRelays() {
		return scanRelays.clone();
	}

	@Override
	public Target[] routeTargets() {
		return routeTargets.clone();
	}

	public static VirtualDataRoomPattern create(long seed) {
		long cursor = mix(seed);
		int[] scan = uniqueIndexes(cursor, RELAY_COUNT, SCAN_COUNT);
		cursor = mix(cursor + 0x9E3779B97F4A7C15L);
		int routeCount = MIN_ROUTE_COUNT + (int) Math.floorMod(cursor, MAX_ROUTE_COUNT - MIN_ROUTE_COUNT + 1);
		Target[] route = new Target[routeCount];
		Target previous = null;
		for (int index = 0; index < route.length; index++) {
			cursor = mix(cursor + index * 53L);
			TargetType type = (cursor & 1L) == 0L ? TargetType.RELAY : TargetType.ANCHOR;
			int bound = type == TargetType.RELAY ? RELAY_COUNT : VirtualDataIntrusionPattern.NODE_COUNT;
			int targetIndex = Math.floorMod((int) (cursor >>> 8), bound);
			Target target = new Target(type, targetIndex);
			if (target.equals(previous)) {
				target = new Target(type, (targetIndex + 1 + index) % bound);
			}
			route[index] = target;
			previous = target;
		}
		return new VirtualDataRoomPattern(scan, route);
	}

	public static Offset relayOffset(int index) {
		return RELAYS[Math.floorMod(index, RELAYS.length)];
	}

	public static Offset cacheOffset(int index) {
		return CACHES[Math.floorMod(index, CACHES.length)];
	}

	public static Offset corruptedOffset(int index) {
		return CORRUPTED[Math.floorMod(index, CORRUPTED.length)];
	}

	public static Offset gateOffset(int index) {
		return GATES[Math.floorMod(index, GATES.length)];
	}

	public static int nearestRelayIndex(int dx, int dy, int dz) {
		return nearestIndex(RELAYS, dx, dy, dz);
	}

	public static int nearestCacheIndex(int dx, int dy, int dz) {
		return nearestIndex(CACHES, dx, dy, dz);
	}

	public static boolean containsRelay(int[] relays, int target) {
		for (int relay : relays) {
			if (relay == target) {
				return true;
			}
		}
		return false;
	}

	public static int rewardMultiplier(int corrosion, long remainingTicks, long totalTicks, int openedCaches) {
		int multiplier = 1 + Math.max(0, Math.min(CACHE_COUNT, openedCaches));
		if (corrosion <= 1) {
			multiplier++;
		}
		if (corrosion == 0) {
			multiplier++;
		}
		if (totalTicks > 0L && remainingTicks * 3L >= totalTicks) {
			multiplier++;
		}
		return Math.max(1, Math.min(6, multiplier));
	}

	public static int[] routeTypes(Target[] targets) {
		return Arrays.stream(targets).mapToInt(target -> target.type().ordinal()).toArray();
	}

	public static int[] routeIndexes(Target[] targets) {
		return Arrays.stream(targets).mapToInt(Target::index).toArray();
	}

	public static Target[] routeTargets(int[] types, int[] indexes) {
		if (types == null || indexes == null || types.length != indexes.length) {
			return new Target[0];
		}
		Target[] targets = new Target[types.length];
		for (int i = 0; i < targets.length; i++) {
			TargetType type = types[i] == TargetType.ANCHOR.ordinal() ? TargetType.ANCHOR : TargetType.RELAY;
			targets[i] = new Target(type, indexes[i]);
		}
		return targets;
	}

	private static int nearestIndex(Offset[] offsets, int dx, int dy, int dz) {
		int best = 0;
		long bestDistance = Long.MAX_VALUE;
		for (int index = 0; index < offsets.length; index++) {
			Offset offset = offsets[index];
			long ox = offset.dx() - dx;
			long oy = offset.dy() - dy;
			long oz = offset.dz() - dz;
			long distance = ox * ox + oy * oy + oz * oz;
			if (distance < bestDistance) {
				best = index;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static int[] uniqueIndexes(long seed, int bound, int count) {
		int[] result = new int[count];
		boolean[] used = new boolean[bound];
		long cursor = seed;
		for (int index = 0; index < count; index++) {
			cursor = mix(cursor + index);
			int value = Math.floorMod((int) cursor, bound);
			while (used[value]) {
				value = (value + 1) % bound;
			}
			used[value] = true;
			result[index] = value;
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

	public enum TargetType {
		RELAY,
		ANCHOR
	}

	public record Target(TargetType type, int index) {
	}

	public record Offset(int dx, int dy, int dz) {
	}
}
