package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.wireless.WirelessNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class WirelessPowerReceiverBlockEntity extends AbstractWirelessPowerBlockEntity {
	private final ReceiverEnergyStorage energyStorage = new ReceiverEnergyStorage();
	private int linkedTowers;
	private int lastTransferAmount;

	public WirelessPowerReceiverBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.WIRELESS_POWER_RECEIVER.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WirelessPowerReceiverBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}
		blockEntity.linkedTowers = WirelessNetworkManager.findTowers(level, blockEntity).size();
		blockEntity.lastTransferAmount = blockEntity.pushEnergyToNeighbors(level, pos, blockEntity.energyStorage.consumeBufferedInput());
	}

	public int currentDemand() {
		int totalDemand = 0;
		for (Direction direction : Direction.values()) {
			IEnergyStorage target = level == null ? null : level.getCapability(Capabilities.EnergyStorage.BLOCK, worldPosition.relative(direction), direction.getOpposite());
			if (target == null || !target.canReceive()) {
				continue;
			}
			totalDemand += Math.max(0, target.receiveEnergy(Integer.MAX_VALUE, true));
		}
		return totalDemand;
	}

	public int relayFromTower(int amount) {
		return energyStorage.acceptInternal(amount);
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

	private int pushEnergyToNeighbors(Level level, BlockPos pos, int availableEnergy) {
		int remaining = Math.max(0, availableEnergy);
		int transferred = 0;
		for (Direction direction : Direction.values()) {
			if (remaining <= 0) {
				break;
			}
			IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(direction), direction.getOpposite());
			if (target == null || !target.canReceive()) {
				continue;
			}
			int accepted = target.receiveEnergy(remaining, false);
			if (accepted > 0) {
				remaining -= accepted;
				transferred += accepted;
			}
		}
		return transferred;
	}

	private final class ReceiverEnergyStorage extends EnergyStorage {
		private int bufferedInput;

		private ReceiverEnergyStorage() {
			super(0, 0, Integer.MAX_VALUE, 0);
		}

		private int acceptInternal(int amount) {
			if (amount <= 0) {
				return 0;
			}
			int accepted = amount;
			if (accepted > 0) {
				bufferedInput += accepted;
				setChanged();
			}
			return accepted;
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
