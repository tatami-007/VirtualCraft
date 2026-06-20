package dev.tatami.virtualcraft.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.tatami.virtualcraft.gameplay.VirtualDataLabyrinth.Corridor;
import dev.tatami.virtualcraft.gameplay.VirtualDataLabyrinth.Room;
import dev.tatami.virtualcraft.gameplay.VirtualDataLabyrinth.RoomType;

class VirtualDataLabyrinthTest {
	@Test
	void layoutIsStableForSeed() {
		VirtualDataLabyrinth first = VirtualDataLabyrinth.generate(987654321L);
		VirtualDataLabyrinth second = VirtualDataLabyrinth.generate(987654321L);

		Room[] a = first.rooms();
		Room[] b = second.rooms();
		assertEquals(a.length, b.length);
		for (int i = 0; i < a.length; i++) {
			assertEquals(a[i], b[i]);
		}
		assertEquals(first.corridors().length, second.corridors().length);
	}

	@Test
	void differentSeedsDiverge() {
		// Not a hard guarantee for every pair, but two unrelated seeds should
		// not produce byte-identical layouts.
		boolean anyDifference = false;
		for (long seed = 1; seed <= 16 && !anyDifference; seed++) {
			anyDifference = VirtualDataLabyrinth.generate(seed).rooms().length
					!= VirtualDataLabyrinth.generate(seed + 1000).rooms().length
					|| !sameCells(VirtualDataLabyrinth.generate(seed), VirtualDataLabyrinth.generate(seed + 1000));
		}
		assertTrue(anyDifference);
	}

	@Test
	void roomCountWithinBounds() {
		for (long seed = 0; seed < 500; seed++) {
			int count = VirtualDataLabyrinth.generate(seed).rooms().length;
			assertTrue(count >= VirtualDataLabyrinth.MIN_ROOMS, "too few rooms: " + count);
			assertTrue(count <= VirtualDataLabyrinth.MAX_ROOMS, "too many rooms: " + count);
		}
	}

	@Test
	void hubIsRoomZeroAtOrigin() {
		for (long seed = 0; seed < 200; seed++) {
			Room hub = VirtualDataLabyrinth.generate(seed).rooms()[0];
			assertEquals(0, hub.id());
			assertEquals(0, hub.gx());
			assertEquals(0, hub.gz());
			assertEquals(0, hub.depth());
			assertEquals(RoomType.HUB, hub.type());
		}
	}

	@Test
	void cellsNeverOverlap() {
		for (long seed = 0; seed < 500; seed++) {
			Room[] rooms = VirtualDataLabyrinth.generate(seed).rooms();
			Set<String> cells = new HashSet<>();
			for (Room room : rooms) {
				assertTrue(cells.add(room.gx() + "," + room.gz()), "overlap at seed " + seed);
			}
		}
	}

	@Test
	void graphIsConnected() {
		for (long seed = 0; seed < 500; seed++) {
			VirtualDataLabyrinth layout = VirtualDataLabyrinth.generate(seed);
			int n = layout.rooms().length;
			boolean[] seen = new boolean[n];
			Deque<Integer> queue = new ArrayDeque<>();
			queue.add(0);
			seen[0] = true;
			int visited = 0;
			while (!queue.isEmpty()) {
				int current = queue.poll();
				visited++;
				for (Corridor corridor : layout.corridors()) {
					int next = -1;
					if (corridor.from() == current) {
						next = corridor.to();
					} else if (corridor.to() == current) {
						next = corridor.from();
					}
					if (next >= 0 && !seen[next]) {
						seen[next] = true;
						queue.add(next);
					}
				}
			}
			assertEquals(n, visited, "disconnected layout at seed " + seed);
		}
	}

	@Test
	void exactlyOneFirewallAndItIsTheDeepestLeaf() {
		for (long seed = 0; seed < 500; seed++) {
			VirtualDataLabyrinth layout = VirtualDataLabyrinth.generate(seed);
			Room[] rooms = layout.rooms();
			boolean[] hasChild = new boolean[rooms.length];
			for (Corridor corridor : layout.corridors()) {
				hasChild[corridor.from()] = true;
			}
			int firewalls = 0;
			Room firewall = null;
			for (Room room : rooms) {
				if (room.type() == RoomType.FIREWALL) {
					firewalls++;
					firewall = room;
				}
			}
			assertEquals(1, firewalls, "expected one firewall at seed " + seed);
			assertTrue(!hasChild[firewall.id()], "firewall must be a leaf at seed " + seed);
			assertEquals(layout.maxDepth(), firewall.depth(), "firewall must be deepest at seed " + seed);
		}
	}

	@Test
	void everyLockedDoorIsControlledByAPuzzleRoom() {
		// This is the solvability guarantee: a locked door is only ever opened
		// by a room that (a) owns a core and (b) is closer to the hub than the
		// door, so it can always be reached and cleared first.
		for (long seed = 0; seed < 500; seed++) {
			VirtualDataLabyrinth layout = VirtualDataLabyrinth.generate(seed);
			Room[] rooms = layout.rooms();
			for (Corridor corridor : layout.corridors()) {
				if (!corridor.locked()) {
					continue;
				}
				Room controller = rooms[corridor.from()];
				Room gated = rooms[corridor.to()];
				assertEquals(RoomType.PUZZLE, controller.type(), "locked door controller must be a puzzle room at seed " + seed);
				assertTrue(controller.depth() < gated.depth(), "controller must be closer to hub at seed " + seed);
			}
		}
	}

	@Test
	void parentDepthIsConsistent() {
		for (long seed = 0; seed < 200; seed++) {
			Room[] rooms = VirtualDataLabyrinth.generate(seed).rooms();
			for (Room room : rooms) {
				if (room.parent() < 0) {
					assertEquals(0, room.depth());
				} else {
					assertEquals(rooms[room.parent()].depth() + 1, room.depth());
				}
			}
		}
	}

	private static boolean sameCells(VirtualDataLabyrinth a, VirtualDataLabyrinth b) {
		Room[] ra = a.rooms();
		Room[] rb = b.rooms();
		if (ra.length != rb.length) {
			return false;
		}
		for (int i = 0; i < ra.length; i++) {
			if (ra[i].gx() != rb[i].gx() || ra[i].gz() != rb[i].gz()) {
				return false;
			}
		}
		return true;
	}
}
