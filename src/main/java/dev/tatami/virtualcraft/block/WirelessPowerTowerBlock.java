package dev.tatami.virtualcraft.block;

import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 无线供电塔方块。1:1 沿用 ME-Beam-Former 1.21.1 (LGPL-3.0) 的
 * {@code WirelessEnergyTowerBlock} 三段多块结构。
 *
 * <p><b>PART</b> 是个 {@link IntegerProperty}：</p>
 * <ul>
 *   <li>{@code 0} = 底部（持有 BlockEntity）</li>
 *   <li>{@code 1} = 中部</li>
 *   <li>{@code 2} = 顶部</li>
 * </ul>
 *
 * <p><b>放置规则</b>：玩家右键放置时检查上方两格可替换（参考 {@link #getStateForPlacement}），
 * 然后 {@link #setPlacedBy} 自动放置中部和顶部。</p>
 *
 * <p><b>破坏规则</b>：{@link #onRemove} 联动消除另两段；{@link #updateShape} 检查相邻段完整性，
 * 任何一段被替换非塔方块（活塞推走、世界生成覆盖等）会自我清除。</p>
 *
 * <p><b>遮挡修正</b>：non-cube 模型用 {@link #noOcclusion} + {@link Shapes#empty()} 占位形状，
 * 避免错误的邻面裁剪与下方阴影瑕疵。</p>
 *
 * <p><b>tick 调度</b>：{@link #getTicker} 返回 {@code null} —— 塔的能量分配由
 * {@code dev.tatami.virtualcraft.energy.WirelessEnergyNetwork} 在
 * {@code ServerTickEvent.Post} 一次性集中处理，避免每个 BE 单独 tick 浪费 CPU。</p>
 */
public class WirelessPowerTowerBlock extends Block implements EntityBlock {

	/** 0=底部，1=中部，2=顶部。1:1 沿用 ME-Beam-Former 的 IntegerProperty。 */
	public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);

	public WirelessPowerTowerBlock(BlockBehaviour.Properties props) {
		super(props);
		this.registerDefaultState(this.stateDefinition.any().setValue(PART, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART);
	}

	// ============================================================
	// 遮挡 / 采光修正（避免非完整方块的视觉瑕疵）
	// ============================================================

	@Override
	public boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		// 配合 noOcclusion 防止错误遮挡与邻面裁剪
		return Shapes.empty();
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	// ============================================================
	// 放置 / 破坏 / 完整性检查
	// ============================================================

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();

		// 检查上方两格是否可以放置
		if (pos.getY() < level.getMaxBuildHeight() - 2
				&& level.getBlockState(pos.above()).canBeReplaced(context)
				&& level.getBlockState(pos.above(2)).canBeReplaced(context)) {
			return this.defaultBlockState().setValue(PART, 0);
		}
		return null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		// 放置中部和顶部
		level.setBlock(pos.above(), state.setValue(PART, 1), 3);
		level.setBlock(pos.above(2), state.setValue(PART, 2), 3);

		// 通知相邻方块（仅底部需要，因为只有底部有 BlockEntity）
		if (!level.isClientSide() && state.getValue(PART) == 0) {
			level.blockUpdated(pos, this);
			for (Direction dir : Direction.values()) {
				level.updateNeighborsAt(pos.relative(dir), this);
			}
		}
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			int part = state.getValue(PART);
			if (part == 0) {
				// 底部被破坏：移除中部和顶部
				if (level.getBlockState(pos.above()).is(this)) {
					level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 35);
				}
				if (level.getBlockState(pos.above(2)).is(this)) {
					level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 35);
				}
			} else if (part == 1) {
				// 中部被破坏：移除底部和顶部
				if (level.getBlockState(pos.below()).is(this)) {
					level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 35);
				}
				if (level.getBlockState(pos.above()).is(this)) {
					level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 35);
				}
			} else if (part == 2) {
				// 顶部被破坏：移除底部和中部
				if (level.getBlockState(pos.below()).is(this)) {
					level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 35);
				}
				if (level.getBlockState(pos.below(2)).is(this)) {
					level.setBlock(pos.below(2), Blocks.AIR.defaultBlockState(), 35);
				}
			}
			super.onRemove(state, level, pos, newState, isMoving);
		}
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
								  BlockPos currentPos, BlockPos neighborPos) {
		int part = state.getValue(PART);

		// 沿 Y 轴检查结构完整性
		if (direction.getAxis() == Direction.Axis.Y) {
			if (part == 0 && direction == Direction.UP) {
				if (!neighborState.is(this) || neighborState.getValue(PART) != 1) {
					return Blocks.AIR.defaultBlockState();
				}
			} else if (part == 1) {
				if (direction == Direction.DOWN && (!neighborState.is(this) || neighborState.getValue(PART) != 0)) {
					return Blocks.AIR.defaultBlockState();
				}
				if (direction == Direction.UP && (!neighborState.is(this) || neighborState.getValue(PART) != 2)) {
					return Blocks.AIR.defaultBlockState();
				}
			} else if (part == 2 && direction == Direction.DOWN) {
				if (!neighborState.is(this) || neighborState.getValue(PART) != 1) {
					return Blocks.AIR.defaultBlockState();
				}
			}
		}
		return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
	}

	// ============================================================
	// BlockEntity（只在底部）
	// ============================================================

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		// 只在底部创建 BlockEntity
		if (state.getValue(PART) == 0) {
			return new WirelessPowerTowerBlockEntity(pos, state);
		}
		return null;
	}

	/**
	 * 不再需要 ticker —— 全局管理器 {@code WirelessEnergyNetwork} 集中处理所有塔的传输。
	 * 这是性能优化的关键：集中调度代替分散 tick，对玩家透明。
	 */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return null;
	}
}
