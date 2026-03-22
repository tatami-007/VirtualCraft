package dev.tatami.virtualcraft.mining;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class MiningSurveyTargetData {
	private static final String ROOT = "survey_target";
	private static final String X = "x";
	private static final String Y = "y";
	private static final String Z = "z";
	private static final String SIZE = "cluster_size";

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

	public static void setTarget(ItemStack stack, BlockPos target, int clusterSize) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag root = new CompoundTag();
			root.putInt(X, target.getX());
			root.putInt(Y, target.getY());
			root.putInt(Z, target.getZ());
			root.putInt(SIZE, Math.max(0, clusterSize));
			tag.put(ROOT, root);
		});
	}

	public static void clear(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(ROOT));
	}

	private static CompoundTag rootTag(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.contains(ROOT) ? tag.getCompound(ROOT) : new CompoundTag();
	}
}
