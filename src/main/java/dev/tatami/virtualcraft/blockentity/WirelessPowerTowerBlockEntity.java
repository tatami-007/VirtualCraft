package dev.tatami.virtualcraft.blockentity;

import java.util.List;

import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.wireless.WirelessNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class WirelessPowerTowerBlockEntity extends AbstractWirelessPowerBlockEntity {
	public static final int MAX_ENERGY = 1_000_000;
	public static final int MAX_RECEIVE = 16_384;
	public static final int TRANSFER_PER_TICK = 4_096;
	private final TowerEnergyStorage energyStorage = new TowerEnergyStorage();
	private int linkedReceivers;

	public WirelessPowerTowerBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.WIRELESS_POWER_TOWER.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WirelessPowerTowerBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}

		List<WirelessPowerReceiverBlockEntity> receivers = WirelessNetworkManager.findReceivers(level, blockEntity);
		blockEntity.linkedReceivers = receivers.size();
		if (!blockEntity.hasBoundLinkCard() || receivers.isEmpty() || blockEntity.energyStorage.getEnergyStored() <= 0) {
			return;
		}

		int transferBudget = Math.min(TRANSFER_PER_TICK, blockEntity.energyStorage.getEnergyStored());
		int share = Math.max(1, transferBudget / receivers.size());
		int transferred = 0;
		int remaining = transferBudget;

		for (WirelessPowerReceiverBlockEntity receiver : receivers) {
			if (remaining <= 0) {
				break;
			}
			int accepted = receiver.receiveFromTower(Math.min(share, remaining));
			transferred += accepted;
			remaining -= accepted;
		}

		if (transferred > 0) {
			blockEntity.energyStorage.extractInternal(transferred);
			blockEntity.setChanged();
		}
	}

	@Override
	public IEnergyStorage getEnergyStorage(Direction side) {
		return energyStorage;
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

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("energy", energyStorage.serializeNBT(registries));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		energyStorage.deserializeNBT(registries, tag.get("energy"));
	}

	private final class TowerEnergyStorage extends EnergyStorage {
		private TowerEnergyStorage() {
			super(MAX_ENERGY, MAX_RECEIVE, 0, 0);
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			int received = super.receiveEnergy(toReceive, simulate);
			if (!simulate && received > 0) {
				setChanged();
			}
			return received;
		}

		private void extractInternal(int amount) {
			energy = Math.max(0, energy - amount);
		}
	}
}
