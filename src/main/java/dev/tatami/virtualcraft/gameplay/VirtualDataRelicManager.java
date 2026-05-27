package dev.tatami.virtualcraft.gameplay;

import java.util.UUID;

import dev.tatami.virtualcraft.blockentity.VirtualDataCoreBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VirtualDataRelicManager {
	private static final int RELIC_DISTANCE = 42;
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
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				int distance = Math.abs(dx) + Math.abs(dz);
				if (distance <= 6) {
					BlockState state = distance >= 5 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.CYAN_STAINED_GLASS.defaultBlockState();
					placeIfAir(level, center.offset(dx, 0, dz), state);
				}
			}
		}
		BlockPos corePos = center.above();
		placeDataBlock(level, corePos, ModBlocks.VIRTUAL_DATA_CORE.get().defaultBlockState());
		if (level.getBlockEntity(corePos) instanceof VirtualDataCoreBlockEntity core) {
			core.initialize(owner, siteIndex);
		}
		for (Direction direction : RELIC_DIRECTIONS) {
			placeDataBlock(level, corePos.relative(direction, 3), ModBlocks.VIRTUAL_DATA_ANCHOR.get().defaultBlockState());
		}
	}

	private static void placeIfAir(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, 3);
		}
	}

	private static void placeDataBlock(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState existing = level.getBlockState(pos);
		if (existing.is(state.getBlock())) {
			return;
		}
		if (existing.isAir() || existing.is(ModBlocks.VIRTUAL_DATA_CORE.get()) || existing.is(ModBlocks.VIRTUAL_DATA_ANCHOR.get())) {
			level.setBlock(pos, state, 3);
		}
	}
}
