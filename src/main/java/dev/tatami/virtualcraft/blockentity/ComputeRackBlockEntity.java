package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.machine.advanced.AutomationItemHandler;
import dev.tatami.virtualcraft.machine.advanced.ComputeProfile;
import dev.tatami.virtualcraft.menu.ComputeRackMenu;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class ComputeRackBlockEntity extends BlockEntity implements MenuProvider {
	public static final int POWER_SLOT = 0;
	public static final int CPU_SLOT_START = 1;
	public static final int CPU_SLOT_END = 3;
	public static final int GPU_SLOT_START = 3;
	public static final int GPU_SLOT_END = 5;
	public static final int MEMORY_SLOT_START = 5;
	public static final int MEMORY_SLOT_END = 9;
	public static final int SLOT_COUNT = 9;

	private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return isValidModule(slot, stack);
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}
	};
	private final IItemHandler automationInventory =
			new AutomationItemHandler(inventory, this::isValidModule, this::modulesLocked);
	private final ContainerData data = new SimpleContainerData(5) {
		@Override
		public int get(int index) {
			ComputeProfile profile = profile();
			return switch (index) {
				case 0 -> profile.onlineRacks();
				case 1 -> profile.cpuCount();
				case 2 -> profile.gpuCount();
				case 3 -> profile.memoryCount();
				case 4 -> profile.parallelLanes();
				default -> 0;
			};
		}
	};

	public ComputeRackBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.COMPUTE_RACK.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ComputeRackBlockEntity blockEntity) {
	}

	public ItemStackHandler getInventory() {
		return inventory;
	}

	public IItemHandler getAutomationInventory() {
		return automationInventory;
	}

	public ContainerData getData() {
		return data;
	}

	public ComputeProfile profile() {
		if (!isOnline()) {
			return ComputeProfile.EMPTY;
		}
		return new ComputeProfile(
				count(CPU_SLOT_START, CPU_SLOT_END, ModItems.CPU.get()),
				count(GPU_SLOT_START, GPU_SLOT_END, ModItems.GPU.get()),
				count(MEMORY_SLOT_START, MEMORY_SLOT_END, ModItems.MEMORY_STICK.get()),
				1);
	}

	public boolean isOnline() {
		return inventory.getStackInSlot(POWER_SLOT).is(ModItems.POWER_SUPPLY.get());
	}

	private int count(int start, int end, net.minecraft.world.item.Item item) {
		int count = 0;
		for (int slot = start; slot < end; slot++) {
			if (inventory.getStackInSlot(slot).is(item)) {
				count++;
			}
		}
		return count;
	}

	private boolean isValidModule(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (slot == POWER_SLOT) {
			return stack.is(ModItems.POWER_SUPPLY.get());
		}
		if (slot >= CPU_SLOT_START && slot < CPU_SLOT_END) {
			return stack.is(ModItems.CPU.get());
		}
		if (slot >= GPU_SLOT_START && slot < GPU_SLOT_END) {
			return stack.is(ModItems.GPU.get());
		}
		return slot >= MEMORY_SLOT_START && slot < MEMORY_SLOT_END
				&& stack.is(ModItems.MEMORY_STICK.get());
	}

	private boolean modulesLocked() {
		if (level == null) {
			return false;
		}
		for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
			if (level.getBlockEntity(worldPosition.relative(direction))
					instanceof QuantumSimulationChamberBlockEntity chamber && chamber.hasActiveProcesses()) {
				return true;
			}
		}
		return false;
	}

	public void dropContents(Level level, BlockPos pos) {
		SimpleContainer container = new SimpleContainer(SLOT_COUNT);
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			container.setItem(slot, inventory.getStackInSlot(slot));
		}
		Containers.dropContents(level, pos, container);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.virtual_craft.compute_rack");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new ComputeRackMenu(containerId, playerInventory, this, data);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inventory.deserializeNBT(registries, tag.getCompound("inventory"));
	}
}
