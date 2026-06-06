package dev.tatami.virtualcraft.block;

import com.mojang.serialization.MapCodec;

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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class VirtualDataAnchorBlock extends Block {
	public static final MapCodec<VirtualDataAnchorBlock> CODEC = simpleCodec(VirtualDataAnchorBlock::new);
	private static final int SEARCH_RADIUS = 4;

	public VirtualDataAnchorBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	public VirtualDataAnchorBlock() {
		this(BlockBehaviour.Properties.of()
				.strength(-1.0F, 3_600_000.0F)
				.lightLevel(state -> 7)
				.noLootTable());
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return handleAnchor(level, pos, player);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult result = handleAnchor(level, pos, player);
		return result.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	private static InteractionResult handleAnchor(Level level, BlockPos pos, Player player) {
		if (!level.isClientSide) {
			VirtualDataCoreBlockEntity core = findCore(level, pos);
			if (core != null) {
				core.handleAnchorInput(player, VirtualDataCoreBlockEntity.nearestNodeIndex(core.getBlockPos(), pos));
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static VirtualDataCoreBlockEntity findCore(Level level, BlockPos anchorPos) {
		VirtualDataCoreBlockEntity nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (BlockPos candidate : BlockPos.betweenClosed(anchorPos.offset(-SEARCH_RADIUS, -1, -SEARCH_RADIUS), anchorPos.offset(SEARCH_RADIUS, 1, SEARCH_RADIUS))) {
			if (level.getBlockEntity(candidate) instanceof VirtualDataCoreBlockEntity core) {
				int distance = candidate.distManhattan(anchorPos);
				if (distance < nearestDistance) {
					nearest = core;
					nearestDistance = distance;
				}
			}
		}
		return nearest;
	}

}
