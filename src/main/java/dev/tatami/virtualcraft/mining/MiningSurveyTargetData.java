package dev.tatami.virtualcraft.mining;

import java.util.Optional;

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

	public static void clearLock(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(ROOT));
	}

	private static CompoundTag rootTag(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.contains(ROOT) ? tag.getCompound(ROOT) : new CompoundTag();
	}
}
