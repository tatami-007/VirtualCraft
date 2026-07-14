package dev.tatami.virtualcraft.blockentity;

import java.util.EnumMap;

import dev.tatami.virtualcraft.block.PowMinerBlock;
import dev.tatami.virtualcraft.machine.IConfigurableSidedMachine;
import dev.tatami.virtualcraft.machine.MachineSideConfig;
import dev.tatami.virtualcraft.machine.PowMinerOutputRules;
import dev.tatami.virtualcraft.machine.SideMode;
import dev.tatami.virtualcraft.energy.WirelessPowerReceiver;
import dev.tatami.virtualcraft.menu.PowMinerMenu;
import dev.tatami.virtualcraft.item.PowerCellItem;
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

public class PowMinerBlockEntity extends BlockEntity implements MenuProvider, IConfigurableSidedMachine, WirelessPowerReceiver {
	public static final int BASIC_OUTPUT_SLOT = PowMinerOutputRules.BASIC_OUTPUT_SLOT;
	public static final int RARE_OUTPUT_SLOT = PowMinerOutputRules.RARE_OUTPUT_SLOT;
	public static final int POWER_CELL_SLOT = 2;
	private static final int OUTPUT_SLOTS = PowMinerOutputRules.OUTPUT_SLOTS;
	private static final int INVENTORY_SLOTS = 3;
	private static final int MAX_PROGRESS = 600;
	private static final int MAX_ENERGY = 200_000;
	private static final int MAX_RECEIVE = 4_000;
	private static final int ENERGY_PER_TICK = 240;
	private static final int TRANSFER_PER_SIDE = 16;
	private static final float ENCRYPTED_BONUS_CHANCE = 0.08F;
	private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOTS) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return slot == POWER_CELL_SLOT && PowerCellItem.isPowerCell(stack);
		}

		@Override
		public int getSlotLimit(int slot) {
			return slot == POWER_CELL_SLOT ? 1 : super.getSlotLimit(slot);
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
	private final EnumMap<Direction, SideMode> sideModes = new EnumMap<>(Direction.class);
	private final EnumMap<Direction, IItemHandler> sidedHandlers = new EnumMap<>(Direction.class);
	private int progress;
	private boolean autoImport;
	private boolean autoExport;
	private final ContainerData sideConfigData = MachineSideConfig.createData(
			sideModes,
			() -> autoImport,
			value -> autoImport = value,
			() -> autoExport,
			value -> autoExport = value);

	public PowMinerBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.POW_MINER.get(), pos, blockState);
		MachineSideConfig.initDefaults(sideModes);
		for (Direction direction : MachineSideConfig.DATA_DIRECTIONS) {
			sidedHandlers.put(direction, new SidedItemHandler(direction));
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, PowMinerBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}
		blockEntity.tickAutomation(level, pos);
		int cellMultiplier = PowerCellItem.multiplier(blockEntity.inventory.getStackInSlot(POWER_CELL_SLOT));
		if (cellMultiplier == 0) {
			return;
		}

		if (!blockEntity.canMine()) {
			if (blockEntity.progress != 0) {
				blockEntity.progress = 0;
				blockEntity.setChanged();
			}
			return;
		}

		int energyCost = ENERGY_PER_TICK * cellMultiplier;
		if (blockEntity.energyStorage.getEnergyStored() < energyCost) {
			return;
		}

		blockEntity.energyStorage.consumeEnergy(energyCost);
		blockEntity.progress += cellMultiplier;
		blockEntity.setChanged();
		if (blockEntity.progress >= MAX_PROGRESS) {
			blockEntity.progress = 0;
			blockEntity.finishMining(level, pos);
		}
	}

	public ItemStackHandler getInventory() {
		return inventory;
	}

	@Override
	public boolean canReceiveWirelessPower() {
		return inventory.getStackInSlot(POWER_CELL_SLOT).is(ModItems.QUANTUM_POWER_CELL.get());
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
		return getBlockState().getValue(PowMinerBlock.FACING);
	}

	@Override
	public Block getMachineBlock() {
		return ModBlocks.POW_MINER.get();
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
		tag.putBoolean("auto_import", autoImport);
		tag.putBoolean("auto_export", autoExport);
		MachineSideConfig.save(tag, sideModes);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		loadInventory(tag.getCompound("inventory"), registries);
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		progress = tag.getInt("progress");
		autoImport = tag.getBoolean("auto_import");
		autoExport = tag.getBoolean("auto_export");
		MachineSideConfig.load(tag, sideModes);
	}

	private void loadInventory(CompoundTag tag, HolderLookup.Provider registries) {
		inventory.deserializeNBT(registries, normalizeInventoryTag(tag));
	}

	static CompoundTag normalizeInventoryTag(CompoundTag tag) {
		CompoundTag normalized = tag.copy();
		normalized.putInt("Size", INVENTORY_SLOTS);
		return normalized;
	}

	private void tickAutomation(Level level, BlockPos pos) {
		if (!autoExport) {
			return;
		}
		for (Direction direction : MachineSideConfig.DATA_DIRECTIONS) {
			if (sideModes.get(direction).allowsExtract()) {
				pushOutputTo(level, pos, direction);
			}
		}
	}

	private void pushOutputTo(Level level, BlockPos pos, Direction direction) {
		IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(direction), direction.getOpposite());
		if (target == null) {
			return;
		}
		for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
			ItemStack output = inventory.getStackInSlot(slot);
			if (output.isEmpty()) {
				continue;
			}
			ItemStack toMove = output.copy();
			toMove.setCount(Math.min(output.getCount(), TRANSFER_PER_SIDE));
			ItemStack remainder = ItemHandlerHelper.insertItem(target, toMove, false);
			int moved = toMove.getCount() - remainder.getCount();
			if (moved > 0) {
				inventory.extractItem(slot, moved, false);
				setChanged();
			}
		}
	}

	private boolean canMine() {
		return canAddVoucher(BASIC_OUTPUT_SLOT, new ItemStack(ModItems.HASH_VOUCHER.get()));
	}

	private void finishMining(Level level, BlockPos pos) {
		if (!addBasicVoucher()) {
			return;
		}
		if (level.random.nextFloat() < ENCRYPTED_BONUS_CHANCE) {
			addVoucher(RARE_OUTPUT_SLOT, new ItemStack(ModItems.ENCRYPTED_HASH_VOUCHER.get()));
		}
	}

	private boolean addBasicVoucher() {
		return addVoucher(BASIC_OUTPUT_SLOT, new ItemStack(ModItems.HASH_VOUCHER.get()));
	}

	private boolean addVoucher(int slot, ItemStack voucher) {
		if (!canAddVoucher(slot, voucher)) {
			return false;
		}
		ItemStack output = inventory.getStackInSlot(slot);
		if (output.isEmpty()) {
			inventory.setStackInSlot(slot, voucher);
			return true;
		}
		output.grow(voucher.getCount());
		inventory.setStackInSlot(slot, output);
		return true;
	}

	private boolean canAddVoucher(int slot, ItemStack voucher) {
		ItemStack output = inventory.getStackInSlot(slot);
		if (output.isEmpty()) {
			return true;
		}
		return ItemStack.isSameItemSameComponents(output, voucher)
				&& output.getCount() + voucher.getCount() <= output.getMaxStackSize();
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
			return stack;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (!sideModes.get(direction).allowsExtract() || slot < 0 || slot >= OUTPUT_SLOTS) {
				return ItemStack.EMPTY;
			}
			return inventory.extractItem(slot, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return inventory.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return false;
		}
	}
}
