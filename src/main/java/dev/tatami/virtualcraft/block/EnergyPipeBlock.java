package dev.tatami.virtualcraft.block;

import java.util.EnumMap;
import java.util.Map;

import dev.tatami.virtualcraft.blockentity.EnergyPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("this-escape")
public final class EnergyPipeBlock extends Block implements EntityBlock {
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
	private static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Direction.class);
	private static final VoxelShape CORE = box(4.75, 4.75, 4.75, 11.25, 11.25, 11.25);
	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

	static {
		CONNECTIONS.put(Direction.DOWN, BlockStateProperties.DOWN);
		CONNECTIONS.put(Direction.UP, BlockStateProperties.UP);
		CONNECTIONS.put(Direction.NORTH, BlockStateProperties.NORTH);
		CONNECTIONS.put(Direction.SOUTH, BlockStateProperties.SOUTH);
		CONNECTIONS.put(Direction.WEST, BlockStateProperties.WEST);
		CONNECTIONS.put(Direction.EAST, BlockStateProperties.EAST);

		ARMS.put(Direction.DOWN, box(5, 0, 5, 11, 5, 11));
		ARMS.put(Direction.UP, box(5, 11, 5, 11, 16, 11));
		ARMS.put(Direction.NORTH, box(5, 5, 0, 11, 11, 5));
		ARMS.put(Direction.SOUTH, box(5, 5, 11, 11, 11, 16));
		ARMS.put(Direction.WEST, box(0, 5, 5, 5, 11, 11));
		ARMS.put(Direction.EAST, box(11, 5, 5, 16, 11, 11));
	}

	public EnergyPipeBlock() {
		super(BlockBehaviour.Properties.of()
				.strength(1.5F, 6.0F)
				.requiresCorrectToolForDrops()
				.noOcclusion());
		BlockState state = stateDefinition.any();
		for (BooleanProperty property : CONNECTIONS.values()) {
			state = state.setValue(property, false);
		}
		registerDefaultState(state.setValue(ACTIVE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CONNECTIONS.values().toArray(BooleanProperty[]::new));
		builder.add(ACTIVE);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return connectionsFor(context.getLevel(), context.getClickedPos(), defaultBlockState());
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
			BlockPos currentPos, BlockPos neighborPos) {
		return state.setValue(CONNECTIONS.get(direction), canConnect(level, neighborPos, direction));
	}

	public static BlockState refreshConnections(Level level, BlockPos pos, BlockState state) {
		return connectionsFor(level, pos, state);
	}

	public static void setActive(Level level, BlockPos pos, boolean active) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof EnergyPipeBlock && state.getValue(ACTIVE) != active) {
			level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_CLIENTS);
		}
	}

	private static BlockState connectionsFor(LevelAccessor level, BlockPos pos, BlockState state) {
		BlockState result = state;
		for (Direction direction : Direction.values()) {
			result = result.setValue(CONNECTIONS.get(direction), canConnect(level, pos.relative(direction), direction));
		}
		return result;
	}

	private static boolean canConnect(LevelAccessor level, BlockPos neighborPos, Direction directionFromPipe) {
		BlockState neighborState = level.getBlockState(neighborPos);
		if (neighborState.getBlock() instanceof EnergyPipeBlock) {
			return true;
		}
		if (level instanceof Level actualLevel) {
			var storage = actualLevel.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, directionFromPipe.getOpposite());
			return storage != null && (storage.canExtract() || storage.canReceive());
		}
		return false;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = CORE;
		for (Direction direction : Direction.values()) {
			if (state.getValue(CONNECTIONS.get(direction))) {
				shape = Shapes.or(shape, ARMS.get(direction));
			}
		}
		return shape;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EnergyPipeBlockEntity(pos, state);
	}
}
