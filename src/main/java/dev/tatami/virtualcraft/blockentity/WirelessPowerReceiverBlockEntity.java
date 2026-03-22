package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.wireless.WirelessNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class WirelessPowerReceiverBlockEntity extends AbstractWirelessPowerBlockEntity {
	public static final int MAX_ENERGY = 200_000;
	public static final int MAX_EXTRACT = 4_096;
	private final ReceiverEnergyStorage energyStorage = new ReceiverEnergyStorage();
	private int linkedTowers;

	public WirelessPowerReceiverBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.WIRELESS_POWER_RECEIVER.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WirelessPowerReceiverBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}
		blockEntity.linkedTowers = WirelessNetworkManager.findTowers(level, blockEntity).size();
		if (blockEntity.energyStorage.getEnergyStored() > 0) {
			blockEntity.pushEnergyToNeighbors(level, pos);
		}
	}

	public int receiveFromTower(int amount) {
		return energyStorage.acceptInternal(amount);
	}

	@Override
	public IEnergyStorage getEnergyStorage(Direction side) {
		return energyStorage;
	}

	@Override
	protected int getLinkedNodeCount() {
		return linkedTowers;
	}

	@Override
	protected String ledgerRole() {
		return "receiver";
	}

	@Override
	protected void registerNode() {
		WirelessNetworkManager.registerReceiver(this);
	}

	@Override
	protected void unregisterNode() {
		WirelessNetworkManager.unregisterReceiver(this);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.virtual_craft.wireless_power_receiver");
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

	private void pushEnergyToNeighbors(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (energyStorage.getEnergyStored() <= 0) {
				break;
			}
			IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(direction), direction.getOpposite());
			if (target == null || !target.canReceive()) {
				continue;
			}
			int offered = Math.min(MAX_EXTRACT, energyStorage.getEnergyStored());
			int accepted = target.receiveEnergy(offered, false);
			if (accepted > 0) {
				energyStorage.extractEnergy(accepted, false);
			}
		}
	}

	private final class ReceiverEnergyStorage extends EnergyStorage {
		private ReceiverEnergyStorage() {
			super(MAX_ENERGY, 0, MAX_EXTRACT, 0);
		}

		private int acceptInternal(int amount) {
			if (amount <= 0) {
				return 0;
			}
			int accepted = Math.min(capacity - energy, amount);
			if (accepted > 0) {
				energy += accepted;
				setChanged();
			}
			return accepted;
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			int extracted = super.extractEnergy(toExtract, simulate);
			if (!simulate && extracted > 0) {
				setChanged();
			}
			return extracted;
		}
	}
}
