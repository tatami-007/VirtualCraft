package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.menu.PowMinerMenu;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PowMinerBlockEntity extends BlockEntity implements MenuProvider {
	public static final int OUTPUT_SLOT = 0;
	private static final int MAX_PROGRESS = 120;
	private static final int MAX_ENERGY = 200_000;
	private static final int MAX_RECEIVE = 4_000;
	private static final int ENERGY_PER_TICK = 240;
	private final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return false;
		}
	};
	private final MinerEnergyStorage energyStorage = new MinerEnergyStorage();
	private final ContainerData data = new SimpleContainerData(4) {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> MAX_PROGRESS;
				case 2 -> energyStorage.getEnergyStored();
				case 3 -> energyStorage.getMaxEnergyStored();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			if (index == 0) {
				progress = value;
			}
		}
	};

	private int progress;

	public PowMinerBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.POW_MINER.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, PowMinerBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}

		if (!blockEntity.canMine()) {
			if (blockEntity.progress != 0) {
				blockEntity.progress = 0;
				blockEntity.setChanged();
			}
			return;
		}

		if (blockEntity.energyStorage.getEnergyStored() < ENERGY_PER_TICK) {
			return;
		}

		blockEntity.energyStorage.consumeEnergy(ENERGY_PER_TICK);
		blockEntity.progress++;
		blockEntity.setChanged();
		if (blockEntity.progress >= MAX_PROGRESS) {
			blockEntity.progress = 0;
			blockEntity.finishMining();
		}
	}

	public ItemStackHandler getInventory() {
		return inventory;
	}

	public IEnergyStorage getEnergyStorage(Direction side) {
		return energyStorage;
	}

	public ContainerData getData() {
		return data;
	}

	public void dropContents(Level level, BlockPos pos) {
		SimpleContainer container = new SimpleContainer(inventory.getSlots());
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			container.setItem(slot, inventory.getStackInSlot(slot));
		}
		Containers.dropContents(level, pos, container);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.virtual_craft.pow_miner");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new PowMinerMenu(containerId, playerInventory, this, data);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
		tag.put("energy", energyStorage.serializeNBT(registries));
		tag.putInt("progress", progress);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inventory.deserializeNBT(registries, tag.getCompound("inventory"));
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		progress = tag.getInt("progress");
	}

	private boolean canMine() {
		ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
		if (output.isEmpty()) {
			return true;
		}
		return output.is(ModItems.HASH_VOUCHER.get()) && output.getCount() < output.getMaxStackSize();
	}

	private void finishMining() {
		ItemStack voucher = new ItemStack(ModItems.HASH_VOUCHER.get());
		ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
		if (output.isEmpty()) {
			inventory.setStackInSlot(OUTPUT_SLOT, voucher);
		} else if (output.is(ModItems.HASH_VOUCHER.get())) {
			output.grow(1);
			inventory.setStackInSlot(OUTPUT_SLOT, output);
		}
	}

	private final class MinerEnergyStorage extends EnergyStorage {
		private MinerEnergyStorage() {
			super(MAX_ENERGY, MAX_RECEIVE, 0, 0);
		}

		private void consumeEnergy(int amount) {
			energy = Math.max(0, energy - amount);
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			int received = super.receiveEnergy(toReceive, simulate);
			if (!simulate && received > 0) {
				setChanged();
			}
			return received;
		}
	}
}
