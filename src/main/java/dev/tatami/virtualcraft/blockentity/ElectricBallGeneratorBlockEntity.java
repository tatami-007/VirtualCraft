package dev.tatami.virtualcraft.blockentity;

import java.util.EnumMap;

import dev.tatami.virtualcraft.block.ElectricBallGeneratorBlock;
import dev.tatami.virtualcraft.machine.IConfigurableSidedMachine;
import dev.tatami.virtualcraft.machine.MachineSideConfig;
import dev.tatami.virtualcraft.machine.SideMode;
import dev.tatami.virtualcraft.menu.ElectricBallGeneratorMenu;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModBlocks;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ElectricBallGeneratorBlockEntity extends BlockEntity implements MenuProvider, IConfigurableSidedMachine {
	private static final int FUEL_SLOT = 0;
	// v18 能量 16× 提升：每球 51,200 → 819,200 FE。
	// MAX_RECEIVE/MAX_EXTRACT 与 ENERGY_PER_TICK 一致避免内部能量溢出丢弃。
	private static final int MAX_ENERGY = 200_000;     // 80_000 ×2.5 — 与电凝聚器一致
	private static final int MAX_EXTRACT = 4_096;      // 2_048 ×2 — 与 ENERGY_PER_TICK 同调
	private static final int BURN_TIME_TOTAL = 200;    // 10 秒燃烧 不变
	private static final int ENERGY_PER_TICK = 4096;   // 256 ×16 — 每球产 819,200 FE
	private static final int TRANSFER_PER_SIDE = 16;
	/** Cached snapshot of {@link Direction#values()}. {@code Direction.values()} clones the
	 *  underlying enum array on every invocation; for a tick-hot loop that runs once per
	 *  generator per tick, that's 6 ints worth of garbage per generator per tick. */
	private static final Direction[] DIRECTIONS = Direction.values();
	private final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
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
	private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
	private final EnumMap<Direction, IItemHandler> sidedHandlers = new EnumMap<>(Direction.class);
	private int burnTimeRemaining;
	private boolean autoImport;
	private boolean autoExport;
	private final ContainerData sideConfigData = MachineSideConfig.createData(
			sideModes,
			() -> autoImport,
			value -> autoImport = value,
			() -> autoExport,
			value -> autoExport = value);

	public ElectricBallGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.ELECTRIC_BALL_GENERATOR.get(), pos, blockState);
		MachineSideConfig.initDefaults(sideModes);
		for (Direction direction : MachineSideConfig.DATA_DIRECTIONS) {
			sidedHandlers.put(direction, new SidedItemHandler(direction));
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ElectricBallGeneratorBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}

		boolean changed = false;
		blockEntity.tickAutomation(level, pos);
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

	public IItemHandler getItemHandler(Direction direction) {
		if (direction == null) {
			return inventory;
		}
		SideMode mode = sideModes.get(direction);
		return mode == SideMode.NONE ? null : sidedHandlers.get(direction);
	}

	public IEnergyStorage getEnergyStorage(Direction side) {
		return energyStorage;
	}

	public ContainerData getData() {
		return data;
	}

	@Override
	public ContainerData getSideConfigData() {
		return sideConfigData;
	}

	@Override
	public SideMode getSideMode(Direction direction) {
		return sideModes.get(direction);
	}

	@Override
	public void cycleSideMode(Direction direction) {
		sideModes.put(direction, sideModes.get(direction).next());
		setChanged();
	}

	@Override
	public void toggleAutoImport() {
		autoImport = !autoImport;
		setChanged();
	}

	@Override
	public void toggleAutoExport() {
		autoExport = !autoExport;
		setChanged();
	}

	@Override
	public Direction getMachineFacing() {
		return getBlockState().getValue(ElectricBallGeneratorBlock.FACING);
	}

	@Override
	public Block getMachineBlock() {
		return ModBlocks.ELECTRIC_BALL_GENERATOR.get();
	}

	@Override
	public MenuProvider createMainMenuProvider() {
		return this;
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
		tag.putBoolean("auto_import", autoImport);
		tag.putBoolean("auto_export", autoExport);

		MachineSideConfig.save(tag, sideModes);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inventory.deserializeNBT(registries, tag.getCompound("inventory"));
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		burnTimeRemaining = tag.getInt("burn_time_remaining");
		autoImport = tag.getBoolean("auto_import");
		autoExport = tag.getBoolean("auto_export");

		MachineSideConfig.load(tag, sideModes);
	}

	private void tickAutomation(Level level, BlockPos pos) {
		if (!autoImport) {
			return;
		}
		for (Direction direction : MachineSideConfig.DATA_DIRECTIONS) {
			if (sideModes.get(direction).allowsInsert()) {
				pullFuelFrom(level, pos, direction);
			}
		}
	}

	private void pullFuelFrom(Level level, BlockPos pos, Direction direction) {
		IItemHandler source = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(direction), direction.getOpposite());
		if (source == null) {
			return;
		}
		for (int slot = 0; slot < source.getSlots(); slot++) {
			ItemStack simulated = source.extractItem(slot, TRANSFER_PER_SIDE, true);
			if (simulated.isEmpty() || !simulated.is(ModItems.ELECTRIC_BALL.get())) {
				continue;
			}
			ItemStack remainder = inventory.insertItem(FUEL_SLOT, simulated, true);
			int accepted = simulated.getCount() - remainder.getCount();
			if (accepted <= 0) {
				continue;
			}
			ItemStack extracted = source.extractItem(slot, accepted, false);
			if (!extracted.isEmpty()) {
				ItemHandlerHelper.insertItem(inventory, extracted, false);
				setChanged();
				return;
			}
		}
	}

	private boolean pushEnergyToNeighbors(Level level, BlockPos pos) {
		boolean moved = false;
		int stored = energyStorage.getEnergyStored();
		if (stored <= 0) {
			return false;
		}
		for (Direction direction : DIRECTIONS) {
			IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(direction), direction.getOpposite());
			if (target == null || !target.canReceive()) {
				continue;
			}
			int offered = Math.min(MAX_EXTRACT, stored);
			int accepted = target.receiveEnergy(offered, false);
			if (accepted > 0) {
				energyStorage.extractEnergy(accepted, false);
				stored -= accepted;
				moved = true;
				if (stored <= 0) {
					break;
				}
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

	private final class SidedItemHandler implements IItemHandler {
		private final Direction direction;

		private SidedItemHandler(Direction direction) {
			this.direction = direction;
		}

		@Override
		public int getSlots() {
			return inventory.getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return inventory.getStackInSlot(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (!sideModes.get(direction).allowsInsert() || slot != FUEL_SLOT) {
				return stack;
			}
			return inventory.insertItem(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return ItemStack.EMPTY;
		}

		@Override
		public int getSlotLimit(int slot) {
			return inventory.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return slot == FUEL_SLOT && inventory.isItemValid(slot, stack);
		}
	}
}
