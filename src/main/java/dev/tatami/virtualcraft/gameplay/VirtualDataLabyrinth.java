package dev.tatami.virtualcraft.gameplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, fully procedural layout for a player's Virtual World data
 * vault. Replaces the old "hub + four identical rooms" sector with a seeded
 * graph of rooms wired together by corridors, so that exploring the dimension
 * becomes a real room-to-room dungeon crawl instead of walking to one of four
 * copies of the same puzzle.
 *
 * <p>This class is intentionally <em>pure</em> — it knows nothing about
 * {@code Level}/{@code BlockPos}/Minecraft and only produces integer grid
 * coordinates and room/corridor metadata. The block-placement layer
 * ({@link VirtualDataRelicManager}) turns this plan into actual blocks. Keeping
 * the generation logic Minecraft-free is what lets it be unit tested off the
 * game thread.</p>
 *
 * <h2>Layout invariants (verified by {@code VirtualDataLabyrinthTest})</h2>
 * <ul>
 *   <li>Room {@code 0} is always the {@link RoomType#HUB} at grid origin.</li>
 *   <li>Rooms form a spanning <em>tree</em> rooted at the hub, so the graph is
 *       connected and no two rooms share a grid cell.</li>
 *   <li>Exactly one {@link RoomType#FIREWALL} room exists — the deepest leaf —
 *       acting as the boss room.</li>
 *   <li>Every <em>locked</em> corridor is controlled by a room that owns a data
 *       core (a {@link RoomType#PUZZLE} room). Because that controlling room is
 *       always strictly closer to the hub than the door it guards, the vault is
 *       always solvable: you can reach and clear the controlling puzzle before
 *       you need the door it opens.</li>
 * </ul>
 */
public record VirtualDataLabyrinth(Room[] rooms, Corridor[] corridors) {
	/** Centre-to-centre spacing between neighbouring rooms on the grid. */
	public static final int CELL = 26;
	/** Half-width of a room shell (walls sit at +/- this from the centre). */
	public static final int ROOM_RADIUS = 6;
	/** Interior height of a room shell. */
	public static final int ROOM_HEIGHT = 6;
	public static final int MIN_ROOMS = 6;
	public static final int MAX_ROOMS = 9;

	private static final int[] DX4 = {1, -1, 0, 0};
	private static final int[] DZ4 = {0, 0, 1, -1};

	public enum RoomType {
		/** Spawn room; has no core, its outgoing corridors are always open. */
		HUB,
		/** Dead-end reward room reached through a locked door. */
		VAULT,
		/** Interior room holding a data core that gates the rooms beyond it. */
		PUZZLE,
		/** Deepest leaf; holds the hardened "firewall" boss core. */
		FIREWALL
	}

	/**
	 * @param id     stable index into {@link #rooms()}
	 * @param gx     grid X (room centre = origin + gx * CELL)
	 * @param gz     grid Z
	 * @param depth  number of corridors between this room and the hub
	 * @param parent room id this room branched off from ({@code -1} for the hub)
	 * @param type   role of the room
	 */
	public record Room(int id, int gx, int gz, int depth, int parent, RoomType type) {
	}

	/**
	 * @param from   hub-side room id; if the corridor is locked this room's core
	 *               is the one that opens the door
	 * @param to     deeper room id
	 * @param locked whether a closed data gate blocks the doorway until the
	 *               {@code from} room's core is cleared
	 */
	public record Corridor(int from, int to, boolean locked) {
	}

	public VirtualDataLabyrinth {
		rooms = rooms == null ? new Room[0] : rooms.clone();
		corridors = corridors == null ? new Corridor[0] : corridors.clone();
	}

	@Override
	public Room[] rooms() {
		return rooms.clone();
	}

	@Override
	public Corridor[] corridors() {
		return corridors.clone();
	}

	public static VirtualDataLabyrinth generate(long seed) {
		long cursor = mix(seed);
		int count = MIN_ROOMS + (int) Math.floorMod(cursor, MAX_ROOMS - MIN_ROOMS + 1);

		// Grow a spanning tree of rooms by repeatedly attaching a new cell to a
		// random existing one. Unique grid cells guarantee no overlap; tree
		// structure guarantees connectivity.
		Map<Long, Integer> occupied = new HashMap<>();
		List<int[]> cells = new ArrayList<>(); // each: {gx, gz, parentId}
		cells.add(new int[]{0, 0, -1});
		occupied.put(packCell(0, 0), 0);

		int attempts = 0;
		while (cells.size() < count && attempts < 4096) {
			cursor = mix(cursor + attempts * 31L + 7L);
			int baseIdx = (int) Math.floorMod(cursor >>> 8, cells.size());
			cursor = mix(cursor + 13L);
			int dir = (int) Math.floorMod(cursor, 4L);
			attempts++;
			int[] base = cells.get(baseIdx);
			int ngx = base[0] + DX4[dir];
			int ngz = base[1] + DZ4[dir];
			if (occupied.containsKey(packCell(ngx, ngz))) {
				continue;
			}
			int id = cells.size();
			cells.add(new int[]{ngx, ngz, baseIdx});
			occupied.put(packCell(ngx, ngz), id);
		}

		int n = cells.size();
		int[] depth = new int[n];
		boolean[] hasChild = new boolean[n];
		for (int i = 1; i < n; i++) {
			int parent = cells.get(i)[2];
			depth[i] = depth[parent] + 1;
			hasChild[parent] = true;
		}

		// Deepest leaf becomes the firewall (boss) room. Ties resolve to the
		// lowest id for determinism.
		int firewall = 0;
		int bestDepth = -1;
		for (int i = 0; i < n; i++) {
			if (!hasChild[i] && depth[i] > bestDepth) {
				bestDepth = depth[i];
				firewall = i;
			}
		}

		RoomType[] type = new RoomType[n];
		type[0] = RoomType.HUB;
		for (int i = 1; i < n; i++) {
			if (i == firewall) {
				type[i] = RoomType.FIREWALL;
			} else if (!hasChild[i]) {
				type[i] = RoomType.VAULT;
			} else {
				type[i] = RoomType.PUZZLE;
			}
		}

		// A corridor is locked iff its hub-side room owns a core (PUZZLE). The
		// hub has no core so its children are always reachable; FIREWALL/VAULT
		// are leaves and never act as a "from", so a locked corridor's
		// controller is guaranteed to be a PUZZLE room.
		Corridor[] corridors = new Corridor[Math.max(0, n - 1)];
		for (int i = 1; i < n; i++) {
			int parent = cells.get(i)[2];
			boolean locked = type[parent] == RoomType.PUZZLE;
			corridors[i - 1] = new Corridor(parent, i, locked);
		}

		Room[] rooms = new Room[n];
		for (int i = 0; i < n; i++) {
			int[] cell = cells.get(i);
			rooms[i] = new Room(i, cell[0], cell[1], depth[i], cell[2], type[i]);
		}
		return new VirtualDataLabyrinth(rooms, corridors);
	}

	/** Greatest room depth in this layout (the firewall room's depth). */
	public int maxDepth() {
		int max = 0;
		for (Room room : rooms) {
			max = Math.max(max, room.depth());
		}
		return max;
	}

	private static long packCell(int gx, int gz) {
		return ((long) (gx + 1024) << 16) | (gz + 1024);
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
