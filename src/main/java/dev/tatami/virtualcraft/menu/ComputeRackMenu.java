package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.ComputeRackBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

@SuppressWarnings("this-escape")
public final class ComputeRackMenu extends AbstractContainerMenu {
	public static final int POWER_X = 17;
	public static final int POWER_Y = 40;
	public static final int CPU_X = 47;
	public static final int GPU_X = 89;
	public static final int MEMORY_X = 131;
	public static final int MODULE_Y = 40;
	private static final int MACHINE_SLOTS = ComputeRackBlockEntity.SLOT_COUNT;
	private static final int PLAYER_START = MACHINE_SLOTS;
	private static final int PLAYER_END = PLAYER_START + 36;

	private final ComputeRackBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public ComputeRackMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(containerId, inventory, resolve(inventory, buffer.readBlockPos()), new SimpleContainerData(5));
	}

	public ComputeRackMenu(int containerId, Inventory playerInventory, ComputeRackBlockEntity blockEntity,
			ContainerData data) {
		super(ModMenuTypes.COMPUTE_RACK.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = playerInventory.player.level();
		this.data = data;
		addSlot(new SlotItemHandler(blockEntity.getInventory(), ComputeRackBlockEntity.POWER_SLOT, POWER_X, POWER_Y));
		for (int index = 0; index < 2; index++) {
			addSlot(new SlotItemHandler(blockEntity.getInventory(), ComputeRackBlockEntity.CPU_SLOT_START + index,
					CPU_X + index * 18, MODULE_Y));
			addSlot(new SlotItemHandler(blockEntity.getInventory(), ComputeRackBlockEntity.GPU_SLOT_START + index,
					GPU_X + index * 18, MODULE_Y));
		}
		for (int index = 0; index < 4; index++) {
			addSlot(new SlotItemHandler(blockEntity.getInventory(), ComputeRackBlockEntity.MEMORY_SLOT_START + index,
					MEMORY_X + (index % 2) * 18, MODULE_Y + (index / 2) * 18));
		}
		addPlayerSlots(playerInventory, 8, 96);
		addDataSlots(data);
	}

	public boolean isOnline() {
		return data.get(0) > 0;
	}

	public int cpuCount() {
		return data.get(1);
	}

	public int gpuCount() {
		return data.get(2);
	}

	public int memoryCount() {
		return data.get(3);
	}

	public int lanes() {
		return data.get(4);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot source = slots.get(index);
		if (!source.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = source.getItem();
		ItemStack copy = stack.copy();
		if (index < MACHINE_SLOTS) {
			if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			int targetSlot = moduleSlot(stack);
			if (targetSlot < 0 || !moveItemStackTo(stack, targetSlot, targetSlot + 1, false)) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			source.set(ItemStack.EMPTY);
		} else {
			source.setChanged();
		}
		return copy;
	}

	private int moduleSlot(ItemStack stack) {
		if (stack.is(ModItems.POWER_SUPPLY.get())) {
			return ComputeRackBlockEntity.POWER_SLOT;
		}
		if (stack.is(ModItems.CPU.get())) {
			return firstOpen(ComputeRackBlockEntity.CPU_SLOT_START, ComputeRackBlockEntity.CPU_SLOT_END);
		}
		if (stack.is(ModItems.GPU.get())) {
			return firstOpen(ComputeRackBlockEntity.GPU_SLOT_START, ComputeRackBlockEntity.GPU_SLOT_END);
		}
		if (stack.is(ModItems.MEMORY_STICK.get())) {
			return firstOpen(ComputeRackBlockEntity.MEMORY_SLOT_START, ComputeRackBlockEntity.MEMORY_SLOT_END);
		}
		return -1;
	}

	private int firstOpen(int start, int end) {
		for (int slot = start; slot < end; slot++) {
			if (blockEntity.getInventory().getStackInSlot(slot).isEmpty()) {
				return slot;
			}
		}
		return start;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
				player, ModBlocks.COMPUTE_RACK.get());
	}

	private static ComputeRackBlockEntity resolve(Inventory inventory, BlockPos pos) {
		BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
		if (blockEntity instanceof ComputeRackBlockEntity rack) {
			return rack;
		}
		throw new IllegalStateException("Expected compute rack at " + pos);
	}

	private void addPlayerSlots(Inventory inventory, int x, int y) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, x + column * 18, y + 58));
		}
	}
}
