package dev.tatami.virtualcraft.gameplay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.tatami.virtualcraft.blockentity.VirtualDataCoreBlockEntity;
import dev.tatami.virtualcraft.entity.VirtualSheep;
import dev.tatami.virtualcraft.gameplay.VirtualDataLabyrinth.Corridor;
import dev.tatami.virtualcraft.gameplay.VirtualDataLabyrinth.Room;
import dev.tatami.virtualcraft.gameplay.VirtualDataLabyrinth.RoomType;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Builds (and idempotently repairs) a player's personal Virtual World vault.
 *
 * <p>The old implementation stamped a fixed hub plus four identical puzzle
 * rooms in the cardinal directions. This version turns the dimension into a real
 * dungeon crawl: {@link VirtualDataLabyrinth} produces a deterministic graph of
 * rooms wired by corridors, and this class renders that graph into blocks —
 * room shells with doorways only where corridors actually connect, enclosed
 * corridors, and closed data gates on the doors that a room's core unlocks when
 * cleared. Deeper rooms scale rewards; the deepest is a hardened firewall boss.</p>
 *
 * <p>Generation is keyed purely off the player UUID, so the layout is stable
 * across visits and every placement is idempotent (existing progress, opened
 * gates and cores are never reset on re-entry).</p>
 */
public final class VirtualDataRelicManager {
	private static final int HUB_SHEEP_TARGET = 3;
	private static final int CELL = VirtualDataLabyrinth.CELL;
	private static final int ROOM_RADIUS = VirtualDataLabyrinth.ROOM_RADIUS;
	private static final int ROOM_HEIGHT = VirtualDataLabyrinth.ROOM_HEIGHT;
	private static final int CORRIDOR_HEIGHT = 3;
	// open[] indices: 0 = NORTH (-Z), 1 = EAST (+X), 2 = SOUTH (+Z), 3 = WEST (-X).
	private static final Direction[] RELIC_DIRECTIONS = {
			Direction.NORTH,
			Direction.EAST,
			Direction.SOUTH,
			Direction.WEST
	};

	private VirtualDataRelicManager() {
	}

	public static BlockPos ensureSector(ServerPlayer player, ServerLevel level) {
		if (!ModDimensions.VIRTUAL_WORLD.equals(level.dimension())) {
			throw new IllegalArgumentException("Virtual data sectors can only be generated in the virtual world");
		}
		BlockPos origin = sectorOrigin(player.getUUID());
		VirtualDataLabyrinth layout = VirtualDataLabyrinth.generate(labyrinthSeed(player.getUUID()));
		Room[] rooms = layout.rooms();
		Corridor[] corridors = layout.corridors();

		// Per-room set of wall directions that a corridor opens into.
		boolean[][] open = new boolean[rooms.length][4];
		// Gate columns each room's core is responsible for unlocking.
		List<List<Long>> controlledGates = new ArrayList<>();
		for (int i = 0; i < rooms.length; i++) {
			controlledGates.add(new ArrayList<>());
		}

		for (Corridor corridor : corridors) {
			Room from = rooms[corridor.from()];
			Room to = rooms[corridor.to()];
			open[from.id()][dirIndex(from, to)] = true;
			open[to.id()][dirIndex(to, from)] = true;
		}

		for (Room room : rooms) {
			buildRoomShell(level, center(origin, room), open[room.id()]);
		}

		for (Corridor corridor : corridors) {
			Room from = rooms[corridor.from()];
			Room to = rooms[corridor.to()];
			List<Long> gates = buildCorridor(level, center(origin, from), center(origin, to), corridor.locked());
			if (corridor.locked()) {
				controlledGates.get(from.id()).addAll(gates);
			}
		}

		for (Room room : rooms) {
			if (room.type() == RoomType.HUB) {
				continue;
			}
			placeCoreRoom(level, center(origin, room), player.getUUID(), room,
					toLongArray(controlledGates.get(room.id())));
		}

		BlockPos hubCenter = center(origin, rooms[0]);
		placeHubFurniture(level, hubCenter);
		ensureHubSheep(level, hubCenter);
		return hubCenter.above();
	}

	private static BlockPos center(BlockPos origin, Room room) {
		return origin.offset(room.gx() * CELL, 0, room.gz() * CELL);
	}

	private static BlockPos sectorOrigin(UUID playerId) {
		VirtualDataSectorMath.SectorOrigin origin = VirtualDataSectorMath.originFor(playerId);
		return new BlockPos(origin.x(), origin.y(), origin.z());
	}

	private static long labyrinthSeed(UUID playerId) {
		return playerId.getMostSignificantBits() ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 23);
	}

	private static int dirIndex(Room from, Room to) {
		int dgx = to.gx() - from.gx();
		int dgz = to.gz() - from.gz();
		if (dgx > 0) {
			return 1; // EAST
		}
		if (dgx < 0) {
			return 3; // WEST
		}
		if (dgz > 0) {
			return 2; // SOUTH
		}
		return 0; // NORTH
	}

	private static void buildRoomShell(ServerLevel level, BlockPos center, boolean[] open) {
		for (int dx = -ROOM_RADIUS; dx <= ROOM_RADIUS; dx++) {
			for (int dz = -ROOM_RADIUS; dz <= ROOM_RADIUS; dz++) {
				for (int dy = 0; dy <= ROOM_HEIGHT; dy++) {
					boolean floor = dy == 0;
					boolean ceiling = dy == ROOM_HEIGHT;
					boolean wall = Math.abs(dx) == ROOM_RADIUS || Math.abs(dz) == ROOM_RADIUS;
					if (!(floor || ceiling || wall)) {
						continue;
					}
					if (isDoorway(dx, dy, dz, open)) {
						continue;
					}
					BlockPos pos = center.offset(dx, dy, dz);
					BlockState state = floor || ceiling || (wall && (Math.abs(dx) + Math.abs(dz)) % 4 == 0)
							? Blocks.SMOOTH_QUARTZ.defaultBlockState()
							: Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
					placeRoomBlock(level, pos, state);
				}
			}
		}
	}

	/** A doorway is a 1-wide, 2-tall gap centred on whichever walls have corridors. */
	private static boolean isDoorway(int dx, int dy, int dz, boolean[] open) {
		if (dy < 1 || dy > 2) {
			return false;
		}
		if (open[0] && dz == -ROOM_RADIUS && dx == 0) {
			return true;
		}
		if (open[1] && dx == ROOM_RADIUS && dz == 0) {
			return true;
		}
		if (open[2] && dz == ROOM_RADIUS && dx == 0) {
			return true;
		}
		return open[3] && dx == -ROOM_RADIUS && dz == 0;
	}

	/**
	 * Carves an enclosed 1-wide tube between two neighbouring room walls. Returns
	 * the packed positions of the gate columns placed at the deeper room's
	 * doorway when {@code locked} — the controller core opens these on success.
	 */
	private static List<Long> buildCorridor(ServerLevel level, BlockPos fromCenter, BlockPos toCenter, boolean locked) {
		int ddx = Integer.compare(toCenter.getX(), fromCenter.getX());
		int ddz = Integer.compare(toCenter.getZ(), fromCenter.getZ());
		BlockPos start = fromCenter.offset(ddx * ROOM_RADIUS, 0, ddz * ROOM_RADIUS);
		BlockPos end = toCenter.offset(-ddx * ROOM_RADIUS, 0, -ddz * ROOM_RADIUS);
		// Perpendicular axis for the tube walls.
		int px = ddx != 0 ? 0 : 1;
		int pz = ddz != 0 ? 0 : 1;

		BlockPos cursor = start;
		while (true) {
			for (int dy = 0; dy <= CORRIDOR_HEIGHT; dy++) {
				boolean floor = dy == 0;
				boolean ceiling = dy == CORRIDOR_HEIGHT;
				if (floor || ceiling) {
					placeIfAir(level, cursor.offset(0, dy, 0), Blocks.SMOOTH_QUARTZ.defaultBlockState());
					placeIfAir(level, cursor.offset(px, dy, pz), Blocks.SMOOTH_QUARTZ.defaultBlockState());
					placeIfAir(level, cursor.offset(-px, dy, -pz), Blocks.SMOOTH_QUARTZ.defaultBlockState());
				} else {
					placeIfAir(level, cursor.offset(px, dy, pz), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
					placeIfAir(level, cursor.offset(-px, dy, -pz), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
				}
			}
			if (cursor.equals(end)) {
				break;
			}
			cursor = cursor.offset(ddx, 0, ddz);
		}

		List<Long> gates = new ArrayList<>();
		if (locked) {
			for (int dy = 1; dy <= 2; dy++) {
				BlockPos gatePos = end.offset(0, dy, 0);
				placeDataBlock(level, gatePos, ModBlocks.VIRTUAL_DATA_GATE.get().defaultBlockState());
				gates.add(gatePos.asLong());
			}
		}
		return gates;
	}

	private static void placeCoreRoom(ServerLevel level, BlockPos center, UUID owner, Room room, long[] controlledGates) {
		BlockPos corePos = center.above();
		placeDataBlock(level, corePos, ModBlocks.VIRTUAL_DATA_CORE.get().defaultBlockState());
		if (level.getBlockEntity(corePos) instanceof VirtualDataCoreBlockEntity core) {
			core.initialize(owner, room.id());
			core.setRoomVersion(VirtualDataRoomPattern.ROOM_VERSION);
			core.setDepth(room.depth());
			core.setBoss(room.type() == RoomType.FIREWALL);
			core.setControlledGates(controlledGates);
		}
		for (Direction direction : RELIC_DIRECTIONS) {
			placeIfAir(level, corePos.relative(direction), Blocks.CYAN_STAINED_GLASS.defaultBlockState());
		}
		for (int node = 0; node < VirtualDataIntrusionPattern.NODE_COUNT; node++) {
			BlockPos anchorPos = VirtualDataCoreBlockEntity.anchorPos(corePos, node);
			placeDataBlock(level, anchorPos, ModBlocks.VIRTUAL_DATA_ANCHOR.get().defaultBlockState());
			placeIfAir(level, midPoint(corePos, anchorPos), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
		}
		for (int index = 0; index < VirtualDataRoomPattern.RELAY_COUNT; index++) {
			placeDataBlock(level, offset(corePos, VirtualDataRoomPattern.relayOffset(index)), ModBlocks.VIRTUAL_DATA_RELAY.get().defaultBlockState());
		}
		for (int index = 0; index < VirtualDataRoomPattern.CACHE_COUNT; index++) {
			placeDataBlock(level, offset(corePos, VirtualDataRoomPattern.cacheOffset(index)), ModBlocks.VIRTUAL_DATA_CACHE.get().defaultBlockState());
		}
		for (int index = 0; index < VirtualDataRoomPattern.CORRUPTED_COUNT; index++) {
			placeDataBlock(level, offset(corePos, VirtualDataRoomPattern.corruptedOffset(index)), ModBlocks.CORRUPTED_DATA_NODE.get().defaultBlockState());
		}
		for (int index = 0; index < VirtualDataRoomPattern.GATE_COUNT; index++) {
			placeDataBlock(level, offset(corePos, VirtualDataRoomPattern.gateOffset(index)), ModBlocks.VIRTUAL_DATA_GATE.get().defaultBlockState());
		}
	}

	private static void placeHubFurniture(ServerLevel level, BlockPos center) {
		placeIfAir(level, center.above(1), Blocks.LIGHT.defaultBlockState());
	}

	private static void ensureHubSheep(ServerLevel level, BlockPos center) {
		AABB bounds = new AABB(center).inflate(10.0, 4.0, 10.0);
		int existing = level.getEntitiesOfClass(VirtualSheep.class, bounds).size();
		for (int index = existing; index < HUB_SHEEP_TARGET; index++) {
			VirtualSheep sheep = ModEntityTypes.VIRTUAL_SHEEP.get().create(level);
			if (sheep == null) {
				return;
			}
			BlockPos spawnPos = center.offset(-3 + index * 3, 1, 3 - index % 2);
			sheep.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
			sheep.setPersistenceRequired();
			level.addFreshEntity(sheep);
		}
	}

	private static long[] toLongArray(List<Long> values) {
		long[] result = new long[values.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = values.get(i);
		}
		return result;
	}

	private static BlockPos midPoint(BlockPos first, BlockPos second) {
		return new BlockPos(
				(first.getX() + second.getX()) / 2,
				Math.min(first.getY(), second.getY()),
				(first.getZ() + second.getZ()) / 2);
	}

	private static BlockPos offset(BlockPos corePos, VirtualDataRoomPattern.Offset offset) {
		return corePos.offset(offset.dx(), offset.dy(), offset.dz());
	}

	private static void placeIfAir(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, 3);
		}
	}

	private static void placeRoomBlock(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState existing = level.getBlockState(pos);
		if (existing.isAir() || isRelicBlock(existing)) {
			level.setBlock(pos, state, 3);
		}
	}

	private static void placeDataBlock(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState existing = level.getBlockState(pos);
		if (existing.is(state.getBlock())) {
			return;
		}
		if (existing.isAir() || isRelicBlock(existing)) {
			level.setBlock(pos, state, 3);
		}
	}

	private static boolean isRelicBlock(BlockState state) {
		return state.is(ModBlocks.VIRTUAL_DATA_CORE.get())
				|| state.is(ModBlocks.VIRTUAL_DATA_ANCHOR.get())
				|| state.is(ModBlocks.VIRTUAL_DATA_RELAY.get())
				|| state.is(ModBlocks.CORRUPTED_DATA_NODE.get())
				|| state.is(ModBlocks.VIRTUAL_DATA_CACHE.get())
				|| state.is(ModBlocks.VIRTUAL_DATA_GATE.get())
				|| state.is(Blocks.SMOOTH_QUARTZ)
				|| state.is(Blocks.SMOOTH_QUARTZ_SLAB)
				|| state.is(Blocks.CYAN_STAINED_GLASS)
				|| state.is(Blocks.LIGHT_BLUE_STAINED_GLASS)
				|| state.is(Blocks.LIGHT);
	}
}
