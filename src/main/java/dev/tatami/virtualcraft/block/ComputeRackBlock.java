package dev.tatami.virtualcraft.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.tatami.virtualcraft.blockentity.ComputeRackBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ComputeRackBlock extends BaseEntityBlock {
	public static final MapCodec<ComputeRackBlock> CODEC = simpleCodec(ComputeRackBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	private ComputeRackBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
	}

	public ComputeRackBlock() {
		this(BlockBehaviour.Properties.of().strength(4.0F, 6.0F).requiresCorrectToolForDrops());
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof ComputeRackBlockEntity rack) {
			serverPlayer.openMenu(rack, buffer -> buffer.writeBlockPos(pos));
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ComputeRackBlockEntity(pos, state);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ComputeRackBlockEntity rack) {
			rack.dropContents(level, pos);
			level.updateNeighbourForOutputSignal(pos, this);
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> blockEntityType) {
		return createTickerHelper(blockEntityType, ModBlockEntities.COMPUTE_RACK.get(), ComputeRackBlockEntity::tick);
	}
}
