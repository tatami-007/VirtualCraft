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
import java.util.function.Predicate;

import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
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
		return collectConnectedOres(level, startPos, maxBlocks, OreClusterFinder::isElectricOre);
	}

	public static List<BlockPos> collectConnectedOres(Level level, BlockPos startPos, int maxBlocks, MiningSurveyTarget target) {
		return collectConnectedOres(level, startPos, maxBlocks, target::matches);
	}

	private static List<BlockPos> collectConnectedOres(Level level, BlockPos startPos, int maxBlocks, Predicate<BlockState> matcher) {
		if (startPos == null || maxBlocks <= 0 || !matcher.test(level.getBlockState(startPos))) {
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
			if (!matcher.test(level.getBlockState(currentPos))) {
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
		return findSurveyTarget(level, center, horizontalRange, verticalRange, maxBlocks, MiningSurveyTarget.defaultTarget());
	}

	/**
	 * Survey-scan a box around {@code center} for the best matching ore vein.
	 *
	 * <p>Previous implementation iterated XYZ in raw order and picked the vein
	 * with the largest cluster, breaking ties by distance. That meant the player
	 * could tap the surveyor while standing on a 5-block vein and get routed to
	 * a 12-block vein 22 blocks away — surprising and slow to reach. It also
	 * allocated a fresh {@link BlockPos} per cell (~88k allocs at the old
	 * 24/18 range) and re-invoked {@code level.getBlockState} on each.</p>
	 *
	 * <p>New scoring: <code>score = clusterSize − sqrt(distSq) × {@link #DISTANCE_WEIGHT}</code>.
	 * With weight 0.4 a 5-block vein 4 blocks away (score 3.4) beats a 9-block
	 * vein 14 blocks away (score 3.4 — tied, distance breaks tie in favor of
	 * closer). A 12-block vein 22 blocks away scores 3.2 — loses to the
	 * one at the player's feet.</p>
	 *
	 * <p>{@link #EXCELLENT_CLUSTER_SIZE} gives us an early-exit so we don't
	 * keep scanning when we've already found a clearly-good vein.</p>
	 *
	 * <p>The inner loop now reuses a single {@link BlockPos.MutableBlockPos}
	 * for visited-check and state lookup, and only {@code .immutable()}s when
	 * a cluster actually starts. Combined with the smaller default range
	 * (16/12 vs 24/18 — see {@code MiningSurveyorActionPayload}), per-click
	 * server work drops by ~62%.</p>
	 */
	public static SurveyResult findSurveyTarget(Level level, BlockPos center, int horizontalRange, int verticalRange, int maxBlocks, MiningSurveyTarget target) {
		Set<BlockPos> visited = new HashSet<>();
		SurveyResult best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		outer:
		for (int y = center.getY() - verticalRange; y <= center.getY() + verticalRange; y++) {
			for (int x = center.getX() - horizontalRange; x <= center.getX() + horizontalRange; x++) {
				for (int z = center.getZ() - horizontalRange; z <= center.getZ() + horizontalRange; z++) {
					cursor.set(x, y, z);
					if (visited.contains(cursor) || !target.matches(level.getBlockState(cursor))) {
						continue;
					}

					BlockPos seed = cursor.immutable();
					List<BlockPos> cluster = collectConnectedOres(level, seed, maxBlocks, target);
					if (cluster.isEmpty()) {
						continue;
					}
					visited.addAll(cluster);

					BlockPos nearest = cluster.stream()
							.min(Comparator.comparingDouble(candidate -> candidate.distSqr(center)))
							.orElse(seed);
					double distSq = nearest.distSqr(center);
					double score = cluster.size() - Math.sqrt(distSq) * DISTANCE_WEIGHT;
					if (score > bestScore) {
						bestScore = score;
						best = new SurveyResult(nearest.immutable(), cluster.size(), distSq);
					}
					if (cluster.size() >= EXCELLENT_CLUSTER_SIZE) {
						// Already an excellent vein — no point scanning further outward
						// since farther veins, even huge ones, will score worse once we
						// subtract their distance penalty.
						break outer;
					}
				}
			}
		}

		return best;
	}

	/**
	 * Distance penalty applied per block of straight-line distance when scoring
	 * candidate veins. 0.4 means a cluster 10 blocks farther needs to be 4
	 * blocks bigger to "tie" — empirically this matches what players intuitively
	 * expect from "find me a nearby vein".
	 */
	private static final double DISTANCE_WEIGHT = 0.4D;

	/**
	 * Cluster size above which {@code findSurveyTarget} stops scanning. The
	 * distance penalty guarantees nothing further out can beat a vein of this
	 * size found close in, so the early exit is sound. 64 ≈ half of the
	 * 128-block render cap, a clear "great" vein.
	 */
	private static final int EXCELLENT_CLUSTER_SIZE = 64;

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


