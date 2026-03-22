package dev.tatami.virtualcraft.blockentity;

import java.util.EnumMap;

import dev.tatami.virtualcraft.block.ElectricCondenserBlock;
import dev.tatami.virtualcraft.machine.CondenserSideMode;
import dev.tatami.virtualcraft.menu.ElectricCondenserMenu;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ElectricCondenserBlockEntity extends BlockEntity implements MenuProvider {
	public static final int INGOT_SLOT = 0;
	public static final int BALL_SLOT = 1;
	public static final int OUTPUT_SLOT = 2;
	public static final int UPGRADE_SLOT_START = 3;
	public static final int UPGRADE_SLOT_END = 6;
	private static final int MAX_PROGRESS = ElectricCondenserTuning.maxProgress();
	private static final int MAX_ENERGY = 200_000;
	private static final int MAX_RECEIVE = 4_000;
	private static final int TRANSFER_PER_SIDE = 16;
	private static final TagKey<Item> INGOTS_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots"));
	private static final Direction[] DATA_DIRECTIONS = {
			Direction.UP,
			Direction.DOWN,
			Direction.NORTH,
			Direction.SOUTH,
			Direction.WEST,
			Direction.EAST
	};

	private final ItemStackHandler inventory = new ItemStackHandler(6) {
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

	private final ContainerData sideConfigData = new SimpleContainerData(8) {
		@Override
		public int get(int index) {
			if (index >= 0 && index < DATA_DIRECTIONS.length) {
				return sideModes.get(DATA_DIRECTIONS[index]).ordinal();
			}
			return switch (index) {
				case 6 -> autoImport ? 1 : 0;
				case 7 -> autoExport ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			if (index >= 0 && index < DATA_DIRECTIONS.length) {
				sideModes.put(DATA_DIRECTIONS[index], CondenserSideMode.values()[Math.max(0, Math.min(CondenserSideMode.values().length - 1, value))]);
			} else if (index == 6) {
				autoImport = value != 0;
			} else if (index == 7) {
				autoExport = value != 0;
			}
		}
	};

	private final EnumMap<Direction, CondenserSideMode> sideModes = new EnumMap<>(Direction.class);
	private final EnumMap<Direction, IItemHandler> sidedHandlers = new EnumMap<>(Direction.class);
	private int progress;
	private boolean autoImport;
	private boolean autoExport;

	public ElectricCondenserBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.ELECTRIC_CONDENSER.get(), pos, blockState);
		for (Direction direction : Direction.values()) {
			sideModes.put(direction, CondenserSideMode.BOTH);
			sidedHandlers.put(direction, new SidedItemHandler(direction));
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ElectricCondenserBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}

		blockEntity.tickAutomation(level, pos);

		boolean active = false;
		if (blockEntity.canCraft()) {
			int energyCost = blockEntity.currentEnergyPerTick();
			if (blockEntity.energyStorage.getEnergyStored() >= energyCost) {
				blockEntity.energyStorage.consumeEnergy(energyCost);
				blockEntity.progress += blockEntity.currentProgressPerTick();
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

	public CondenserSideMode getSideMode(Direction direction) {
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
		CondenserSideMode mode = sideModes.get(direction);
		return mode == CondenserSideMode.NONE ? null : sidedHandlers.get(direction);
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

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
		tag.put("energy", energyStorage.serializeNBT(registries));
		tag.putInt("progress", progress);
		tag.putBoolean("auto_import", autoImport);
		tag.putBoolean("auto_export", autoExport);

		CompoundTag sideTag = new CompoundTag();
		for (Direction direction : Direction.values()) {
			sideTag.putString(direction.getName(), sideModes.get(direction).name());
		}
		tag.put("side_modes", sideTag);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inventory.deserializeNBT(registries, tag.getCompound("inventory"));
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		progress = tag.getInt("progress");
		autoImport = tag.getBoolean("auto_import");
		autoExport = tag.getBoolean("auto_export");

		for (Direction direction : Direction.values()) {
			sideModes.put(direction, CondenserSideMode.BOTH);
		}
		if (tag.contains("side_modes")) {
			CompoundTag sideTag = tag.getCompound("side_modes");
			for (Direction direction : Direction.values()) {
				if (sideTag.contains(direction.getName())) {
					try {
						sideModes.put(direction, CondenserSideMode.valueOf(sideTag.getString(direction.getName())));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		}
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

		for (Direction direction : Direction.values()) {
			CondenserSideMode mode = sideModes.get(direction);
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

	private int currentProgressPerTick() {
		return ElectricCondenserTuning.progressPerTick(countSpeedCards());
	}

	private int currentEnergyPerTick() {
		return ElectricCondenserTuning.energyPerTick(countSpeedCards());
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
			CondenserSideMode mode = sideModes.get(direction);
			if (!mode.allowsInsert() || slot == OUTPUT_SLOT || slot >= UPGRADE_SLOT_START) {
				return stack;
			}
			return inventory.insertItem(slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			CondenserSideMode mode = sideModes.get(direction);
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
