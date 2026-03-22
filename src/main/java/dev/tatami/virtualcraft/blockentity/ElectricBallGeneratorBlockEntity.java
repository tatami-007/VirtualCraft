package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.menu.ElectricBallGeneratorMenu;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ElectricBallGeneratorBlockEntity extends BlockEntity implements MenuProvider {
	private static final int FUEL_SLOT = 0;
	private static final int MAX_ENERGY = 80_000;
	private static final int MAX_EXTRACT = 2_048;
	private static final int BURN_TIME_TOTAL = 200;
	private static final int ENERGY_PER_TICK = 100;
	private final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
			return stack.is(ModItems.ELECTRIC_BALL.get());
		}
	};
	private final StarterEnergyStorage energyStorage = new StarterEnergyStorage();
	private final ContainerData data = new SimpleContainerData(4) {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> energyStorage.getEnergyStored();
				case 1 -> energyStorage.getMaxEnergyStored();
				case 2 -> burnTimeRemaining;
				case 3 -> BURN_TIME_TOTAL;
				default -> 0;
			};
		}
	};

	private int burnTimeRemaining;

	public ElectricBallGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.ELECTRIC_BALL_GENERATOR.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ElectricBallGeneratorBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}

		boolean changed = false;
		if (blockEntity.burnTimeRemaining > 0) {
			int accepted = blockEntity.energyStorage.addEnergy(ENERGY_PER_TICK);
			if (accepted > 0) {
				blockEntity.burnTimeRemaining--;
				changed = true;
			}
			if (blockEntity.burnTimeRemaining <= 0) {
				blockEntity.burnTimeRemaining = 0;
				changed = true;
			}
		}

		if (blockEntity.burnTimeRemaining <= 0 && blockEntity.energyStorage.getEnergyStored() <= MAX_ENERGY - ENERGY_PER_TICK) {
			net.minecraft.world.item.ItemStack stack = blockEntity.inventory.getStackInSlot(FUEL_SLOT);
			if (stack.is(ModItems.ELECTRIC_BALL.get())) {
				blockEntity.inventory.extractItem(FUEL_SLOT, 1, false);
				blockEntity.burnTimeRemaining = BURN_TIME_TOTAL;
				changed = true;
			}
		}

		if (blockEntity.energyStorage.getEnergyStored() > 0) {
			changed |= blockEntity.pushEnergyToNeighbors(level, pos);
		}

		if (changed) {
			blockEntity.setChanged();
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
		return Component.translatable("block.virtual_craft.electric_ball_generator");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new ElectricBallGeneratorMenu(containerId, playerInventory, this, data);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
		tag.put("energy", energyStorage.serializeNBT(registries));
		tag.putInt("burn_time_remaining", burnTimeRemaining);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inventory.deserializeNBT(registries, tag.getCompound("inventory"));
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		burnTimeRemaining = tag.getInt("burn_time_remaining");
	}

	private boolean pushEnergyToNeighbors(Level level, BlockPos pos) {
		boolean moved = false;
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
				moved = true;
			}
		}
		return moved;
	}

	private final class StarterEnergyStorage extends EnergyStorage {
		private StarterEnergyStorage() {
			super(MAX_ENERGY, 0, MAX_EXTRACT, 0);
		}

		private int addEnergy(int amount) {
			if (amount <= 0) {
				return 0;
			}
			int accepted = Math.min(capacity - energy, amount);
			if (accepted > 0) {
				energy += accepted;
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
