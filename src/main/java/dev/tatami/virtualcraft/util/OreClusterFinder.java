package dev.tatami.virtualcraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.tatami.virtualcraft.registry.ModBlocks;

public final class OreClusterFinder {
	private static final int[][] NEIGHBOR_OFFSETS = createNeighborOffsets();

	private OreClusterFinder() {
	}

	public record SurveyResult(BlockPos anchor, int clusterSize, double distanceSquared) {
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

	public static SurveyResult findSurveyTarget(Level level, BlockPos center, int horizontalRange, int verticalRange, int maxBlocks) {
		Set<BlockPos> visited = new HashSet<>();
		SurveyResult best = null;

		for (int y = center.getY() - verticalRange; y <= center.getY() + verticalRange; y++) {
			for (int x = center.getX() - horizontalRange; x <= center.getX() + horizontalRange; x++) {
				for (int z = center.getZ() - horizontalRange; z <= center.getZ() + horizontalRange; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (visited.contains(pos) || !isElectricOre(level.getBlockState(pos))) {
						continue;
					}

					List<BlockPos> cluster = collectConnectedOres(level, pos, maxBlocks);
					if (cluster.isEmpty()) {
						continue;
					}
					visited.addAll(cluster);

					BlockPos nearest = cluster.stream()
							.min(Comparator.comparingDouble(candidate -> candidate.distSqr(center)))
							.orElse(pos);
					SurveyResult candidate = new SurveyResult(nearest.immutable(), cluster.size(), nearest.distSqr(center));
					if (best == null
							|| candidate.clusterSize() > best.clusterSize()
							|| (candidate.clusterSize() == best.clusterSize() && candidate.distanceSquared() < best.distanceSquared())) {
						best = candidate;
					}
				}
			}
		}

		return best;
	}

	public static boolean isElectricOre(BlockState state) {
		return state.is(ModBlocks.ELECTRIC_ORE.get()) || state.is(ModBlocks.DEEPSLATE_ELECTRIC_ORE.get());
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


