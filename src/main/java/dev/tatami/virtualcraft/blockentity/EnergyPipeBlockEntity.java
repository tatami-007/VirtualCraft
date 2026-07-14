package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.energy.EnergyPipeNetwork;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EnergyPipeBlockEntity extends BlockEntity {
	private static final int ACTIVE_HOLD_TICKS = 5;
	private long lastTransferTick = Long.MIN_VALUE;

	public EnergyPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ENERGY_PIPE.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level instanceof ServerLevel serverLevel) {
			EnergyPipeNetwork.getInstance().register(serverLevel, worldPosition);
		}
	}

	@Override
	public void setRemoved() {
		if (level instanceof ServerLevel serverLevel) {
			EnergyPipeNetwork.getInstance().unregister(serverLevel, worldPosition);
		}
		super.setRemoved();
	}

	public boolean updateActivity(ServerLevel level, boolean transferred) {
		if (transferred) {
			lastTransferTick = level.getGameTime();
		}
		return lastTransferTick != Long.MIN_VALUE && level.getGameTime() - lastTransferTick <= ACTIVE_HOLD_TICKS;
	}
}
