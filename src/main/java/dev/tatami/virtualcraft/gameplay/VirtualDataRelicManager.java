package dev.tatami.virtualcraft.gameplay;

import java.util.UUID;

import dev.tatami.virtualcraft.blockentity.VirtualDataCoreBlockEntity;
import dev.tatami.virtualcraft.entity.VirtualSheep;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class VirtualDataRelicManager {
	private static final int RELIC_DISTANCE = 42;
	private static final int ROOM_RADIUS = 6;
	private static final int ROOM_HEIGHT = 6;
	private static final int HUB_SHEEP_TARGET = 3;
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
		ensureHub(level, origin);
		ensureHubSheep(level, origin);
		for (int index = 0; index < RELIC_DIRECTIONS.length; index++) {
			Direction direction = RELIC_DIRECTIONS[index];
			BlockPos center = origin.relative(direction, RELIC_DISTANCE);
			ensureBridge(level, origin, center);
			ensureRelic(level, center, player.getUUID(), index);
		}
		return origin.above();
	}

	private static BlockPos sectorOrigin(UUID playerId) {
		VirtualDataSectorMath.SectorOrigin origin = VirtualDataSectorMath.originFor(playerId);
		return new BlockPos(origin.x(), origin.y(), origin.z());
	}

	private static void ensureHub(ServerLevel level, BlockPos origin) {
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				int max = Math.max(Math.abs(dx), Math.abs(dz));
				BlockState state = max == 5 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
				placeIfAir(level, origin.offset(dx, 0, dz), state);
			}
		}
		placeIfAir(level, origin.above(1), Blocks.LIGHT.defaultBlockState());
	}

	private static void ensureHubSheep(ServerLevel level, BlockPos origin) {
		AABB bounds = new AABB(origin).inflate(10.0, 4.0, 10.0);
		int existing = level.getEntitiesOfClass(VirtualSheep.class, bounds).size();
		for (int index = existing; index < HUB_SHEEP_TARGET; index++) {
			VirtualSheep sheep = ModEntityTypes.VIRTUAL_SHEEP.get().create(level);
			if (sheep == null) {
				return;
			}
			BlockPos spawnPos = origin.offset(-3 + index * 3, 1, 3 - index % 2);
			sheep.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
			sheep.setPersistenceRequired();
			level.addFreshEntity(sheep);
		}
	}

	private static void ensureBridge(ServerLevel level, BlockPos from, BlockPos to) {
		int dx = Integer.compare(to.getX(), from.getX());
		int dz = Integer.compare(to.getZ(), from.getZ());
		BlockPos cursor = from;
		while (!cursor.equals(to)) {
			cursor = cursor.offset(dx, 0, dz);
			placeIfAir(level, cursor, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
			if (dx != 0) {
				placeIfAir(level, cursor.north(), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
			} else {
				placeIfAir(level, cursor.east(), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
			}
		}
	}

	private static void ensureRelic(ServerLevel level, BlockPos center, UUID owner, int siteIndex) {
		ensureRoomShell(level, center);
		BlockPos corePos = center.above();
		placeDataBlock(level, corePos, ModBlocks.VIRTUAL_DATA_CORE.get().defaultBlockState());
		if (level.getBlockEntity(corePos) instanceof VirtualDataCoreBlockEntity core) {
			core.initialize(owner, siteIndex);
			core.setRoomVersion(VirtualDataRoomPattern.ROOM_VERSION);
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

	private static void ensureRoomShell(ServerLevel level, BlockPos center) {
		for (int dx = -ROOM_RADIUS; dx <= ROOM_RADIUS; dx++) {
			for (int dz = -ROOM_RADIUS; dz <= ROOM_RADIUS; dz++) {
				for (int dy = 0; dy <= ROOM_HEIGHT; dy++) {
					boolean floor = dy == 0;
					boolean ceiling = dy == ROOM_HEIGHT;
					boolean wall = Math.abs(dx) == ROOM_RADIUS || Math.abs(dz) == ROOM_RADIUS;
					BlockPos pos = center.offset(dx, dy, dz);
					if (floor || ceiling || wall) {
						if (dz == -ROOM_RADIUS && dy >= 1 && dy <= 3 && Math.abs(dx) <= 1) {
							continue;
						}
						BlockState state = floor || ceiling || wall && (Math.abs(dx) + Math.abs(dz)) % 4 == 0
								? Blocks.SMOOTH_QUARTZ.defaultBlockState()
								: Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
						placeRoomBlock(level, pos, state);
					} else if (dy == 1 && (Math.abs(dx) == 5 || Math.abs(dz) == 5)) {
						placeIfAir(level, pos, Blocks.CYAN_STAINED_GLASS.defaultBlockState());
					}
				}
			}
		}
		for (int dz = -ROOM_RADIUS - 3; dz <= -ROOM_RADIUS; dz++) {
			placeIfAir(level, center.offset(0, 0, dz), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
			placeIfAir(level, center.offset(-1, 0, dz), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
			placeIfAir(level, center.offset(1, 0, dz), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
		}
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
