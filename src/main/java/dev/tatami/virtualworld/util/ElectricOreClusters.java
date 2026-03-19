package dev.tatami.virtualworld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.tatami.virtualworld.core.definitions.VWBlocks;

public final class ElectricOreClusters {
	private static final int[][] NEIGHBOR_OFFSETS = createNeighborOffsets();

	private ElectricOreClusters() {
	}

	public static BlockPos findTargetedOre(Level level, LivingEntity entity, double range) {
		BlockHitResult hitResult = level.clip(new ClipContext(entity.getEyePosition(1f), entity.getEyePosition(1f).add(entity.getViewVector(1f).scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
		if (hitResult.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		BlockPos targetPos = hitResult.getBlockPos();
		return isElectricOre(level.getBlockState(targetPos)) ? targetPos : null;
	}

	public static List<BlockPos> collectConnectedOres(Level level, BlockPos startPos, int maxBlocks) {
		if (startPos == null || maxBlocks <= 0 || !isElectricOre(level.getBlockState(startPos))) {
			return List.of();
		}

		List<BlockPos> connectedOres = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();

		BlockPos immutableStartPos = startPos.immutable();
		queue.add(immutableStartPos);
		visited.add(immutableStartPos);

		while (!queue.isEmpty() && connectedOres.size() < maxBlocks) {
			BlockPos currentPos = queue.removeFirst();
			if (!isElectricOre(level.getBlockState(currentPos))) {
				continue;
			}

			connectedOres.add(currentPos);
			for (int[] offset : NEIGHBOR_OFFSETS) {
				BlockPos nextPos = currentPos.offset(offset[0], offset[1], offset[2]).immutable();
				if (visited.add(nextPos)) {
					queue.addLast(nextPos);
				}
			}
		}

		return connectedOres;
	}

	public static boolean isElectricOre(BlockState state) {
		return state.is(VWBlocks.ELECTRIC_ORE.get()) || state.is(VWBlocks.DEEPSLATE_ELECTRIC_ORE.get());
	}

	private static int[][] createNeighborOffsets() {
		int[][] offsets = new int[26][3];
		int index = 0;
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if (x == 0 && y == 0 && z == 0) {
						continue;
					}
					offsets[index][0] = x;
					offsets[index][1] = y;
					offsets[index][2] = z;
					index++;
				}
			}
		}
		return offsets;
	}
}
