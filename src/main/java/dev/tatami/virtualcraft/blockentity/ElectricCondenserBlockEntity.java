package dev.tatami.virtualcraft.blockentity;

import java.util.EnumMap;

import dev.tatami.virtualcraft.block.ElectricCondenserBlock;
import dev.tatami.virtualcraft.machine.IConfigurableSidedMachine;
import dev.tatami.virtualcraft.machine.ElectricCondenserTuning;
import dev.tatami.virtualcraft.machine.MachineSideConfig;
import dev.tatami.virtualcraft.machine.SideMode;
import dev.tatami.virtualcraft.menu.ElectricCondenserMenu;
import dev.tatami.virtualcraft.item.PowerCellItem;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
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

public class ElectricCondenserBlockEntity extends BlockEntity implements MenuProvider, IConfigurableSidedMachine {
	public static final int INGOT_SLOT = 0;
	public static final int BALL_SLOT = 1;
	public static final int OUTPUT_SLOT = 2;
	public static final int UPGRADE_SLOT_START = 3;
	public static final int UPGRADE_SLOT_END = 6;
	public static final int POWER_CELL_SLOT = 6;
	private static final int MAX_PROGRESS = ElectricCondenserTuning.maxProgress();
	private static final int MAX_ENERGY = 200_000;
	private static final int MAX_RECEIVE = 4_000;
	private static final int TRANSFER_PER_SIDE = 16;
	private static final TagKey<Item> INGOTS_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots"));

	private final ItemStackHandler inventory = new ItemStackHandler(7) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return switch (slot) {
				case INGOT_SLOT -> isValidIngot(stack);
				case BALL_SLOT -> stack.is(ModItems.ELECTRIC_BALL.get());
				case OUTPUT_SLOT -> false;
				case 3, 4, 5 -> stack.is(ModItems.SPEED_UPGRADE_CARD.get());
				case POWER_CELL_SLOT -> PowerCellItem.isPowerCell(stack);
				default -> false;
			};
		}

		@Override
		public int getSlotLimit(int slot) {
			return slot >= UPGRADE_SLOT_START ? 1 : super.getSlotLimit(slot);
		}
	};

	private final CondenserEnergyStorage energyStorage = new CondenserEnergyStorage();
	private final ContainerData data = new SimpleContainerData(5) {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> MAX_PROGRESS;
				case 2 -> energyStorage.getEnergyStored();
				case 3 -> energyStorage.getMaxEnergyStored();
				case 4 -> countSpeedCards();
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
	public ElectricCondenserBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.ELECTRIC_CONDENSER.get(), pos, blockState);
		MachineSideConfig.initDefaults(sideModes);
		for (Direction direction : MachineSideConfig.DATA_DIRECTIONS) {
			sidedHandlers.put(direction, new SidedItemHandler(direction));
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ElectricCondenserBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}

		blockEntity.tickAutomation(level, pos);
		int cellMultiplier = PowerCellItem.multiplier(blockEntity.inventory.getStackInSlot(POWER_CELL_SLOT));
		if (cellMultiplier == 0) {
			blockEntity.updateActiveState(level, state, false);
			return;
		}

		boolean active = false;
		if (blockEntity.canCraft()) {
			// Hoist countSpeedCards() out of the per-tick path: it iterates 3
			// upgrade slots and is otherwise computed up to 4× per tick (energy,
			// progress, ContainerData GUI sync, plus any debug call). With the
			// snapshot, the inner code stays branch-free.
			int speedCards = blockEntity.countSpeedCards();
			int energyCost = ElectricCondenserTuning.energyPerTick(speedCards) * cellMultiplier;
			if (blockEntity.energyStorage.getEnergyStored() >= energyCost) {
				blockEntity.energyStorage.consumeEnergy(energyCost);
				blockEntity.progress += ElectricCondenserTuning.progressPerTick(speedCards) * cellMultiplier;
				active = true;
				blockEntity.setChanged();
				if (blockEntity.progress >= MAX_PROGRESS) {
					blockEntity.progress = 0;
					blockEntity.craftItem();
				}
			}
		} else if (blockEntity.progress != 0) {
			blockEntity.progress = 0;
			blockEntity.setChanged();
		}

		blockEntity.updateActiveState(level, state, active);
	}

	public ItemStackHandler getInventory() {
		return inventory;
	}

	public IEnergyStorage getEnergyStorage(Direction side) {
		return energyStorage;
	}

	public static boolean isValidIngot(ItemStack stack) {
		return stack.is(INGOTS_TAG) && !stack.is(ModItems.ELECTRIC_INGOT.get());
	}

	public ContainerData getData() {
		return data;
	}

	public ContainerData getSideConfigData() {
		return sideConfigData;
	}

	public SideMode getSideMode(Direction direction) {
		return sideModes.get(direction);
	}

	public void cycleSideMode(Direction direction) {
		sideModes.put(direction, sideModes.get(direction).next());
		setChanged();
	}

	public boolean isAutoImport() {
		return autoImport;
	}

	public boolean isAutoExport() {
		return autoExport;
	}

	public void toggleAutoImport() {
		autoImport = !autoImport;
		setChanged();
	}

	public void toggleAutoExport() {
		autoExport = !autoExport;
		setChanged();
	}

	public IItemHandler getItemHandler(Direction direction) {
		if (direction == null) {
			return inventory;
		}
		SideMode mode = sideModes.get(direction);
		return mode == SideMode.NONE ? null : sidedHandlers.get(direction);
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
		return Component.translatable("block.virtual_craft.electric_condenser");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new ElectricCondenserMenu(containerId, playerInventory, this, data);
	}

	// ==================================================================
	// IConfigurableSidedMachine 接口实现
	// (getSideMode / cycleSideMode / toggleAutoImport / toggleAutoExport / getSideConfigData 在上面已实现)
	// ==================================================================

	@Override
	public Direction getMachineFacing() {
		return getBlockState().getValue(ElectricCondenserBlock.FACING);
	}

	@Override
	public Block getMachineBlock() {
		return ModBlocks.ELECTRIC_CONDENSER.get();
	}

	@Override
	public MenuProvider createMainMenuProvider() {
		// BE 自身就是 MenuProvider（createMenu 返回 ElectricCondenserMenu），BACK 按钮直接复用。
		return this;
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
		CompoundTag inventoryTag = tag.getCompound("inventory").copy();
		inventoryTag.putInt("Size", 7);
		inventory.deserializeNBT(registries, inventoryTag);
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		progress = tag.getInt("progress");
		autoImport = tag.getBoolean("auto_import");
		autoExport = tag.getBoolean("auto_export");

		MachineSideConfig.load(tag, sideModes);
	}

	private void updateActiveState(Level level, BlockState state, boolean active) {
		if (state.getValue(ElectricCondenserBlock.ACTIVE) != active) {
			level.setBlock(worldPosition, state.setValue(ElectricCondenserBlock.ACTIVE, active), 3);
		}
	}

	private boolean canCraft() {
		ItemStack ingot = inventory.getStackInSlot(INGOT_SLOT);
		ItemStack ball = inventory.getStackInSlot(BALL_SLOT);
		ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
		if (ingot.isEmpty() || ball.isEmpty()) {
			return false;
		}
		if (!isValidIngot(ingot) || !ball.is(ModItems.ELECTRIC_BALL.get())) {
			return false;
		}

		ItemStack result = new ItemStack(ModItems.ELECTRIC_INGOT.get());
		if (output.isEmpty()) {
			return true;
		}

		return ItemStack.isSameItemSameComponents(output, result) && output.getCount() < output.getMaxStackSize();
	}

	private void craftItem() {
		if (!canCraft()) {
			return;
		}

		inventory.extractItem(INGOT_SLOT, 1, false);
		inventory.extractItem(BALL_SLOT, 1, false);

		ItemStack result = new ItemStack(ModItems.ELECTRIC_INGOT.get());
		ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
		if (output.isEmpty()) {
			inventory.setStackInSlot(OUTPUT_SLOT, result);
		} else {
			output.grow(1);
			inventory.setStackInSlot(OUTPUT_SLOT, output);
		}
	}

	private void tickAutomation(Level level, BlockPos pos) {
		if (!autoImport && !autoExport) {
			return;
		}

		for (Direction direction : MachineSideConfig.DATA_DIRECTIONS) {
			SideMode mode = sideModes.get(direction);
			if (autoExport && mode.allowsExtract()) {
				pushOutputTo(level, pos, direction);
			}
			if (autoImport && mode.allowsInsert()) {
				pullInputsFrom(level, pos, direction);
			}
		}
	}

	private void pushOutputTo(Level level, BlockPos pos, Direction direction) {
		ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
		if (output.isEmpty()) {
			return;
		}

		IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(direction), direction.getOpposite());
		if (target == null) {
			return;
		}

		ItemStack toMove = output.copy();
		toMove.setCount(Math.min(output.getCount(), TRANSFER_PER_SIDE));
		ItemStack remainder = ItemHandlerHelper.insertItem(target, toMove, false);
		int moved = toMove.getCount() - remainder.getCount();
		if (moved > 0) {
			inventory.extractItem(OUTPUT_SLOT, moved, false);
			setChanged();
		}
	}

	private void pullInputsFrom(Level level, BlockPos pos, Direction direction) {
		IItemHandler source = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(direction), direction.getOpposite());
		if (source == null) {
			return;
		}

		for (int slot = 0; slot < source.getSlots(); slot++) {
			ItemStack simulated = source.extractItem(slot, TRANSFER_PER_SIDE, true);
			if (simulated.isEmpty()) {
				continue;
			}

			int targetSlot = getInputSlotFor(simulated);
			if (targetSlot == -1) {
				continue;
			}

			ItemStack remainder = inventory.insertItem(targetSlot, simulated, true);
			int accepted = simulated.getCount() - remainder.getCount();
			if (accepted <= 0) {
				continue;
			}

			ItemStack extracted = source.extractItem(slot, accepted, false);
			if (extracted.isEmpty()) {
				continue;
			}
			inventory.insertItem(targetSlot, extracted, false);
			setChanged();
			return;
		}
	}

	private int getInputSlotFor(ItemStack stack) {
		if (stack.is(ModItems.ELECTRIC_BALL.get())) {
			return BALL_SLOT;
		}
		if (isValidIngot(stack)) {
			return INGOT_SLOT;
		}
		return -1;
	}

	private int countSpeedCards() {
		int count = 0;
		for (int slot = UPGRADE_SLOT_START; slot < UPGRADE_SLOT_END; slot++) {
			if (inventory.getStackInSlot(slot).is(ModItems.SPEED_UPGRADE_CARD.get())) {
				count++;
			}
		}
		return count;
	}

	// {@code currentProgressPerTick / currentEnergyPerTick} were thin wrappers that
	// each re-walked the upgrade slots. The tick path now caches countSpeedCards()
	// once and feeds it to ElectricCondenserTuning directly, so the wrappers were
	// removed to eliminate duplicated slot iteration on the hot path.

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
			SideMode mode = sideModes.get(direction);
			if (!mode.allowsInsert() || slot == OUTPUT_SLOT || slot >= UPGRADE_SLOT_START) {
				return stack;
			}
			return inventory.insertItem(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			SideMode mode = sideModes.get(direction);
			if (!mode.allowsExtract() || slot != OUTPUT_SLOT) {
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
			return slot != OUTPUT_SLOT && slot < UPGRADE_SLOT_START && inventory.isItemValid(slot, stack);
		}
	}

	private final class CondenserEnergyStorage extends EnergyStorage {
		private CondenserEnergyStorage() {
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
