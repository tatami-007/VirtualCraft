package dev.tatami.virtualcraft.client.highlight;

import java.util.Optional;

import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import net.minecraft.core.BlockPos;

public final class MiningSurveyorClientState {
	private static BlockPos targetPos;
	private static MiningSurveyTarget target;
	private static int clusterSize;

	private MiningSurveyorClientState() {
	}

	public static void lock(BlockPos pos, MiningSurveyTarget surveyTarget, int size) {
		targetPos = pos.immutable();
		target = surveyTarget;
		clusterSize = Math.max(0, size);
	}

	public static void clear() {
		targetPos = null;
		target = null;
		clusterSize = 0;
	}

	public static Optional<BlockPos> targetPos() {
		return Optional.ofNullable(targetPos);
	}

	public static Optional<MiningSurveyTarget> target() {
		return Optional.ofNullable(target);
	}

	public static int clusterSize() {
		return clusterSize;
	}
}
