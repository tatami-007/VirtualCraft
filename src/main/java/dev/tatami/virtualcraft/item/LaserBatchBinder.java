package dev.tatami.virtualcraft.item;

import dev.tatami.virtualcraft.block.WirelessPowerTowerBlock;
import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class LaserBatchBinder {
	private static final int BFS_VISIT_LIMIT = 512;

	private LaserBatchBinder() {
	}

	public static void bulkBindNeighbors(ServerPlayer player, BlockPos start) {
		if (player == null || start == null) {
			return;
		}
		Level level = player.level();
		if (level == null || level.isClientSide()) {
			return;
		}

		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof LaserBindingTool)) {
			stack = player.getOffhandItem();
			if (!(stack.getItem() instanceof LaserBindingTool)) {
				return;
			}
		}

		CompoundTag tag = LaserBindingTool.readTag(stack);
		if (!tag.contains(LaserBindingTool.TAG_SOURCE) || !LaserBindingTool.TYPE_TOWER.equals(tag.getString(LaserBindingTool.TAG_SOURCE_TYPE))) {
			LaserBindingTool.msg(player, "tooltip.virtual_craft.binding.no_source");
			return;
		}

		CompoundTag posTag = tag.getCompound(LaserBindingTool.TAG_SOURCE);
		BlockPos source = new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"));
		WirelessPowerTowerBlockEntity sourceEntity = LaserBindingTool.getTowerBlockEntity(level, source);
		if (sourceEntity == null) {
			tag.remove(LaserBindingTool.TAG_SOURCE);
			tag.remove(LaserBindingTool.TAG_SOURCE_TYPE);
			LaserBindingTool.writeTag(stack, tag);
			LaserBindingTool.msg(player, "tooltip.virtual_craft.binding.invalid");
			return;
		}

		BlockEntity startBE = level.getBlockEntity(start);
		if (startBE == null
				|| level.getBlockState(start).getBlock() instanceof WirelessPowerTowerBlock
				|| !LaserBindingTool.hasEnergyCapability(startBE)) {
			LaserBindingTool.msg(player, "tooltip.virtual_craft.binding.batch_none");
			return;
		}

		boolean unbindMode = sourceEntity.getLinks().contains(start);
		Set<BlockPos> machineGroup = collectMachineGroup(level, start);
		BatchResult result = applyBatch(sourceEntity, source, machineGroup, unbindMode);

		CompoundTag lockTag = LaserBindingTool.readTag(stack);
		lockTag.putBoolean(LaserBindingTool.TAG_BATCH_LOCK, true);
		LaserBindingTool.writeTag(stack, lockTag);

		if (result.affected() > 0) {
			String key = unbindMode
					? "tooltip.virtual_craft.binding.batch_unbound"
					: "tooltip.virtual_craft.binding.batch_bound_bfs";
			LaserBindingTool.msg(player, key, result.affected(), machineGroup.size(), result.outOfRangeCount());
		} else if (result.outOfRangeCount() > 0) {
			LaserBindingTool.msg(player, "tooltip.virtual_craft.binding.batch_all_out_of_range", result.outOfRangeCount());
		} else {
			LaserBindingTool.msg(player, "tooltip.virtual_craft.binding.batch_none");
		}
	}

	private static Set<BlockPos> collectMachineGroup(Level level, BlockPos start) {
		Set<BlockPos> machineGroup = new HashSet<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		BlockPos startImmutable = start.immutable();
		ChunkPos startChunk = new ChunkPos(startImmutable);
		queue.add(startImmutable);
		machineGroup.add(startImmutable);

		while (!queue.isEmpty() && machineGroup.size() < BFS_VISIT_LIMIT) {
			BlockPos current = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos neighbor = current.relative(dir).immutable();
				if (machineGroup.contains(neighbor)) {
					continue;
				}
				if (neighbor.getX() >> 4 != startChunk.x || neighbor.getZ() >> 4 != startChunk.z) {
					continue;
				}
				if (level.getBlockState(neighbor).getBlock() instanceof WirelessPowerTowerBlock) {
					continue;
				}
				BlockEntity nbe = level.getBlockEntity(neighbor);
				if (nbe == null || !LaserBindingTool.hasEnergyCapability(nbe)) {
					continue;
				}
				machineGroup.add(neighbor);
				queue.add(neighbor);
			}
		}
		return machineGroup;
	}

	private static BatchResult applyBatch(WirelessPowerTowerBlockEntity sourceEntity, BlockPos source, Set<BlockPos> machineGroup, boolean unbindMode) {
		int affected = 0;
		int outOfRangeCount = 0;
		for (BlockPos pos : machineGroup) {
			if (pos.equals(source)) {
				continue;
			}
			if (!LaserBindingTool.isInTowerRange(source, pos)) {
				outOfRangeCount++;
				continue;
			}
			boolean alreadyBound = sourceEntity.getLinks().contains(pos);
			if (unbindMode) {
				if (alreadyBound) {
					sourceEntity.removeLink(pos);
					affected++;
				}
			} else if (!alreadyBound) {
				sourceEntity.addLink(pos);
				affected++;
			}
		}
		return new BatchResult(affected, outOfRangeCount);
	}

	private record BatchResult(int affected, int outOfRangeCount) {
	}
}
