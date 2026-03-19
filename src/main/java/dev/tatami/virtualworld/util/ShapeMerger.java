package dev.tatami.virtualworld.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ShapeMerger {
	private static final Long2ObjectMap<Direction> BY_NORMAL = Arrays.stream(Direction.values())
			.collect(Collectors.toMap(
					dir -> new BlockPos(dir.getNormal()).asLong(),
					dir -> dir,
					(dir, other) -> {
						throw new IllegalArgumentException("Duplicate keys");
					},
					Long2ObjectOpenHashMap::new));

	private double regionBuildingAxis = Double.NEGATIVE_INFINITY;
	private double faceBuildingAxis = Double.NEGATIVE_INFINITY;
	private Vec3 lastCenterPoint;
	private AABB currentBox;

	private final Map<Vec3, AABB> boxAssignments = Maps.newHashMap();
	private final Multimap<AABB, Vec3> mergerAssignments = HashMultimap.create();

	public static Collection<AABB> merge(Collection<BlockPos> positions, BlockPos origin) {
		ShapeMerger merger = new ShapeMerger();
		positions.stream()
				.map(pos -> pos.subtract(origin))
				.sorted()
				.map(AABB::new)
				.forEachOrdered(aabb -> {
					if (merger.regionBuildingAxis != aabb.minX) {
						merger.setCurrentBox(null, null);
					}
					merger.regionBuildingAxis = aabb.minX;

					if (merger.faceBuildingAxis != aabb.minY) {
						merger.setCurrentBox(null, null);
					}
					merger.faceBuildingAxis = aabb.minY;

					Optional<Vec3> previousCenterPoint = Optional.ofNullable(merger.lastCenterPoint);
					Vec3 centerPoint = aabb.getCenter();
					merger.lastCenterPoint = centerPoint;

					Optional<Direction> moveNext = previousCenterPoint.map(prev -> {
						Vec3 delta = centerPoint.subtract(prev);
						BlockPos blockDelta = BlockPos.containing(delta);
						return BY_NORMAL.get(BlockPos.asLong(blockDelta.getX(), blockDelta.getY(), blockDelta.getZ()));
					});

					if (merger.currentBox != null && moveNext.map(dir -> areBoxesNeighbors(merger.currentBox, aabb, dir)).orElse(false)) {
						merger.expandCurrentBoxTowards(aabb, centerPoint);
						if (merger.mergeNeighbors(centerPoint, merger.currentBox)) {
							return;
						}
						return;
					}

					if (merger.mergeNeighbors(centerPoint, aabb)) {
						return;
					}

					merger.setCurrentBox(aabb, centerPoint);
				});
		return merger.mergerAssignments.keySet();
	}

	private static boolean areBoxesNeighbors(AABB left, AABB right, Direction direction) {
		double endOfLeft = getDirectionalValue(left, direction);
		double startOfRight = getDirectionalValue(right, direction.getOpposite());
		if (endOfLeft != startOfRight) {
			return false;
		}

		for (Direction dir : Direction.values()) {
			if (dir.getAxis() != direction.getAxis() && getDirectionalValue(left, dir) != getDirectionalValue(right, dir)) {
				return false;
			}
		}
		return true;
	}

	private static double getDirectionalValue(AABB box, Direction direction) {
		return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(direction.getAxis()) : box.min(direction.getAxis());
	}

	private void setCurrentBox(AABB box, Vec3 centerPoint) {
		currentBox = box;
		if (box != null && centerPoint != null) {
			boxAssignments.put(centerPoint, box);
			mergerAssignments.put(box, centerPoint);
		}
	}

	private void expandCurrentBoxTowards(AABB target, Vec3 centerPoint) {
		AABB expanded = currentBox.minmax(target);
		Collection<Vec3> assignedPoints = mergerAssignments.removeAll(currentBox);
		assignedPoints.forEach(point -> boxAssignments.put(point, expanded));
		mergerAssignments.putAll(expanded, assignedPoints);
		boxAssignments.put(centerPoint, expanded);
		mergerAssignments.put(expanded, centerPoint);
		currentBox = expanded;
	}

	private boolean mergeNeighbors(Vec3 centerPoint, AABB aabb) {
		for (Direction direction : Direction.values()) {
			Vec3 neighborCenter = centerPoint.add(Vec3.atLowerCornerOf(direction.getNormal()));
			AABB neighborBox = boxAssignments.get(neighborCenter);
			if (neighborBox != null && areBoxesNeighbors(aabb, neighborBox, direction)) {
				expandBoxAt(neighborCenter, aabb, centerPoint);
				return true;
			}
		}
		return false;
	}

	private void expandBoxAt(Vec3 neighborCenter, AABB entryData, Vec3 centerPoint) {
		AABB current = boxAssignments.get(neighborCenter);
		AABB expanded = current.minmax(entryData);
		Collection<Vec3> assignedPoints = mergerAssignments.removeAll(current);
		assignedPoints.forEach(point -> boxAssignments.put(point, expanded));
		mergerAssignments.putAll(expanded, assignedPoints);
		boxAssignments.put(centerPoint, expanded);
		mergerAssignments.put(expanded, centerPoint);
		currentBox = expanded;
	}
}
