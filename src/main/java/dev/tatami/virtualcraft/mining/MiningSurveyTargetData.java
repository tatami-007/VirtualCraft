package dev.tatami.virtualcraft.mining;

import java.util.List;
import java.util.Optional;

import dev.tatami.virtualcraft.machine.advanced.QuantumScanService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class MiningSurveyTargetData {
	private static final String ROOT = "survey_target";
	private static final String SELECTED = "survey_selected";
	private static final String X = "x";
	private static final String Y = "y";
	private static final String Z = "z";
	private static final String SIZE = "cluster_size";
	private static final String TARGET = "target";
	private static final String RESULTS = "results";
	private static final String RESULT_COUNTS = "result_counts";

	private MiningSurveyTargetData() {
	}

	public static Optional<BlockPos> target(ItemStack stack) {
		CompoundTag root = rootTag(stack);
		if (!root.contains(X) || !root.contains(Y) || !root.contains(Z)) {
			return Optional.empty();
		}
		return Optional.of(new BlockPos(root.getInt(X), root.getInt(Y), root.getInt(Z)));
	}

	public static int clusterSize(ItemStack stack) {
		return Math.max(0, rootTag(stack).getInt(SIZE));
	}

	public static MiningSurveyTarget selectedTarget(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return MiningSurveyTarget.byKey(tag.getString(SELECTED)).orElse(MiningSurveyTarget.defaultTarget());
	}

	public static MiningSurveyTarget lockedTarget(ItemStack stack) {
		CompoundTag root = rootTag(stack);
		return MiningSurveyTarget.byKey(root.getString(TARGET)).orElse(selectedTarget(stack));
	}

	public static void setSelectedTarget(ItemStack stack, MiningSurveyTarget target) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(SELECTED, target.key()));
	}

	public static void setTarget(ItemStack stack, BlockPos target, int clusterSize) {
		setTarget(stack, target, clusterSize, selectedTarget(stack));
	}

	public static void setTarget(ItemStack stack, BlockPos target, int clusterSize, MiningSurveyTarget surveyTarget) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag root = new CompoundTag();
			root.putInt(X, target.getX());
			root.putInt(Y, target.getY());
			root.putInt(Z, target.getZ());
			root.putInt(SIZE, Math.max(0, clusterSize));
			root.putString(TARGET, surveyTarget.key());
			tag.put(ROOT, root);
		});
	}

	public static void setResults(ItemStack stack, List<QuantumScanService.ScanPoint> points,
			MiningSurveyTarget surveyTarget) {
		if (points.isEmpty()) {
			clearLock(stack);
			return;
		}
		setTarget(stack, points.getFirst().pos(), points.getFirst().count(), surveyTarget);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag root = tag.getCompound(ROOT);
			root.putLongArray(RESULTS, points.stream().mapToLong(point -> point.pos().asLong()).toArray());
			root.putIntArray(RESULT_COUNTS, points.stream().mapToInt(QuantumScanService.ScanPoint::count).toArray());
			tag.put(ROOT, root);
		});
	}

	public static List<QuantumScanService.ScanPoint> results(ItemStack stack) {
		CompoundTag root = rootTag(stack);
		long[] positions = root.getLongArray(RESULTS);
		int[] counts = root.getIntArray(RESULT_COUNTS);
		if (positions.length == 0) {
			return target(stack)
					.map(pos -> List.of(new QuantumScanService.ScanPoint(pos, clusterSize(stack))))
					.orElseGet(List::of);
		}
		java.util.ArrayList<QuantumScanService.ScanPoint> points = new java.util.ArrayList<>(positions.length);
		for (int index = 0; index < positions.length; index++) {
			points.add(new QuantumScanService.ScanPoint(BlockPos.of(positions[index]),
					index < counts.length ? counts[index] : 0));
		}
		return List.copyOf(points);
	}

	public static void clearLock(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(ROOT));
	}

	private static CompoundTag rootTag(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.contains(ROOT) ? tag.getCompound(ROOT) : new CompoundTag();
	}
}
