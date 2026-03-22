package dev.tatami.virtualcraft.blockentity;

import java.util.List;

import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.wireless.WirelessTransferPlanner;
import dev.tatami.virtualcraft.wireless.WirelessNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class WirelessPowerTowerBlockEntity extends AbstractWirelessPowerBlockEntity {
	private final TowerEnergyStorage energyStorage = new TowerEnergyStorage();
	private int linkedReceivers;
	private int lastTransferAmount;

	public WirelessPowerTowerBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.WIRELESS_POWER_TOWER.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WirelessPowerTowerBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}

		List<WirelessPowerReceiverBlockEntity> receivers = WirelessNetworkManager.findReceivers(level, blockEntity);
		blockEntity.linkedReceivers = receivers.size();
		blockEntity.lastTransferAmount = 0;
		if (!blockEntity.hasBoundLinkCard() || receivers.isEmpty()) {
			return;
		}

		int availableEnergy = blockEntity.energyStorage.consumeBufferedInput();
		if (availableEnergy <= 0) {
			return;
		}

		List<Integer> demand = receivers.stream().map(WirelessPowerReceiverBlockEntity::currentDemand).toList();
		List<Integer> planned = WirelessTransferPlanner.planTransfers(availableEnergy, demand);
		int transferred = 0;
		for (int index = 0; index < receivers.size(); index++) {
			int accepted = receivers.get(index).relayFromTower(planned.get(index));
			transferred += accepted;
		}
		blockEntity.lastTransferAmount = transferred;
		if (transferred > 0) {
			blockEntity.setChanged();
		}
	}

	@Override
	public IEnergyStorage getEnergyStorage(Direction side) {
		return energyStorage;
	}

	@Override
	protected int getDisplayedEnergyStored() {
		return lastTransferAmount;
	}

	@Override
	protected int getDisplayedMaxEnergyStored() {
		return 0;
	}

	@Override
	protected int getLinkedNodeCount() {
		return linkedReceivers;
	}

	@Override
	protected String ledgerRole() {
		return "tower";
	}

	@Override
	protected void registerNode() {
		WirelessNetworkManager.registerTower(this);
	}

	@Override
	protected void unregisterNode() {
		WirelessNetworkManager.unregisterTower(this);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.virtual_craft.wireless_power_tower");
	}

	private final class TowerEnergyStorage extends EnergyStorage {
		private int bufferedInput;

		private TowerEnergyStorage() {
			super(0, Integer.MAX_VALUE, 0, 0);
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			int received = Math.max(0, toReceive);
			if (!simulate && received > 0) {
				bufferedInput += received;
				setChanged();
			}
			return received;
		}

		@Override
		public int getEnergyStored() {
			return 0;
		}

		@Override
		public int getMaxEnergyStored() {
			return 0;
		}

		private int consumeBufferedInput() {
			int available = bufferedInput;
			bufferedInput = 0;
			return available;
		}
	}
}
