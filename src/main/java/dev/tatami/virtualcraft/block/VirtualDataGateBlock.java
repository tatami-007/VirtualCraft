package dev.tatami.virtualcraft.block;

import com.mojang.serialization.MapCodec;

import dev.tatami.virtualcraft.blockentity.VirtualDataCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VirtualDataGateBlock extends Block {
	public static final MapCodec<VirtualDataGateBlock> CODEC = simpleCodec(VirtualDataGateBlock::new);
	public static final BooleanProperty OPEN = BooleanProperty.create("open");

	public VirtualDataGateBlock() {
		this(BlockBehaviour.Properties.of()
				.strength(-1.0F, 3_600_000.0F)
				.sound(SoundType.GLASS)
				.lightLevel(state -> state.getValue(OPEN) ? 3 : 8)
				.noLootTable());
	}

	public VirtualDataGateBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(OPEN, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(OPEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : super.getShape(state, level, pos, context);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : super.getCollisionShape(state, level, pos, context);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide && VirtualDataRoomNodeBlock.findCore(level, pos) instanceof VirtualDataCoreBlockEntity core) {
			core.showStatus(player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OPEN);
	}
}
