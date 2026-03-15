package dev.tatami.virtual_world.block;

import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;

import dev.tatami.virtual_world.procedures.ElectricOreBrokenByPlayerProcedure;

public class DeepslateElectricOreBlock extends Block {
	public DeepslateElectricOreBlock() {
		super(BlockBehaviour.Properties.of().strength(1f, 10f));
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
		return 15;
	}

	@Override
	public boolean onDestroyedByPlayer(BlockState blockstate, Level world, BlockPos pos, Player entity, boolean willHarvest, FluidState fluid) {
		boolean retval = super.onDestroyedByPlayer(blockstate, world, pos, entity, willHarvest, fluid);
		ElectricOreBrokenByPlayerProcedure.execute(world, entity);
		return retval;
	}
}
