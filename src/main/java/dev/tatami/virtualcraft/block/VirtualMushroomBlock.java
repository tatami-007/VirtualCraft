package dev.tatami.virtualcraft.block;

import com.mojang.serialization.MapCodec;

import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VirtualMushroomBlock extends BushBlock {
	public static final MapCodec<VirtualMushroomBlock> CODEC = simpleCodec(VirtualMushroomBlock::new);
	private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);
	private final float spreadRate;

	public VirtualMushroomBlock(BlockBehaviour.Properties properties) {
		this(properties, 1.0F);
	}

	public VirtualMushroomBlock(BlockBehaviour.Properties properties, float spreadRate) {
		super(properties);
		this.spreadRate = spreadRate;
	}

	@Override
	protected MapCodec<? extends BushBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.DIRT)
				|| state.is(BlockTags.MUSHROOM_GROW_BLOCK)
				|| state.is(ModBlocks.VIRTUAL_GRASS_BLOCK.get())
				|| state.is(ModBlocks.VIRTUAL_DIRT.get());
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos soilPos = pos.below();
		BlockState soil = level.getBlockState(soilPos);
		if (soil.is(BlockTags.MUSHROOM_GROW_BLOCK)) {
			return true;
		}
		return level.getRawBrightness(pos, 0) < 13 && mayPlaceOn(soil, level, soilPos);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int spreadChance = Math.max(1, Math.round(25.0F / spreadRate));
		if (random.nextInt(spreadChance) != 0) {
			return;
		}

		int nearbyLimit = 5;
		for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
			if (level.getBlockState(nearby).is(this) && --nearbyLimit <= 0) {
				return;
			}
		}

		BlockPos candidate = pos.offset(
				random.nextInt(3) - 1,
				random.nextInt(2) - random.nextInt(2),
				random.nextInt(3) - 1);
		for (int attempt = 0; attempt < 4; attempt++) {
			if (level.isEmptyBlock(candidate) && state.canSurvive(level, candidate)) {
				pos = candidate;
			}
			candidate = pos.offset(
					random.nextInt(3) - 1,
					random.nextInt(2) - random.nextInt(2),
					random.nextInt(3) - 1);
		}
		if (level.isEmptyBlock(candidate) && state.canSurvive(level, candidate)) {
			level.setBlock(candidate, state, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
