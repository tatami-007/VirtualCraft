package dev.tatami.virtualcraft.block;

import dev.tatami.virtualcraft.blockentity.VirtualDataCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public abstract class VirtualDataRoomNodeBlock extends Block {
	private static final int SEARCH_RADIUS = 8;
	private final NodeType nodeType;

	protected VirtualDataRoomNodeBlock(NodeType nodeType) {
		this(BlockBehaviour.Properties.of()
				.strength(-1.0F, 3_600_000.0F)
				.sound(SoundType.GLASS)
				.lightLevel(state -> nodeType.lightLevel())
				.noLootTable(),
				nodeType);
	}

	protected VirtualDataRoomNodeBlock(BlockBehaviour.Properties properties, NodeType nodeType) {
		super(properties);
		this.nodeType = nodeType;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return handle(level, pos, player);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult result = handle(level, pos, player);
		return result.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	private InteractionResult handle(Level level, BlockPos pos, Player player) {
		if (!level.isClientSide && findCore(level, pos) instanceof VirtualDataCoreBlockEntity core) {
			core.handleRoomNodeInput(player, nodeType, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	static VirtualDataCoreBlockEntity findCore(Level level, BlockPos nodePos) {
		VirtualDataCoreBlockEntity nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (BlockPos candidate : BlockPos.betweenClosed(nodePos.offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS), nodePos.offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
			if (level.getBlockEntity(candidate) instanceof VirtualDataCoreBlockEntity core) {
				int distance = candidate.distManhattan(nodePos);
				if (distance < nearestDistance) {
					nearest = core;
					nearestDistance = distance;
				}
			}
		}
		return nearest;
	}

	public enum NodeType {
		RELAY(9),
		CORRUPTED(6),
		CACHE(10);

		private final int lightLevel;

		NodeType(int lightLevel) {
			this.lightLevel = lightLevel;
		}

		int lightLevel() {
			return lightLevel;
		}
	}
}
