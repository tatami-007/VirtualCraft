package dev.tatami.virtualcraft.machine.advanced;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class AutomationItemHandler implements IItemHandler {
	private final ItemStackHandler inventory;
	private final BiPredicate<Integer, ItemStack> validator;
	private final BooleanSupplier extractionLocked;

	public AutomationItemHandler(ItemStackHandler inventory, BiPredicate<Integer, ItemStack> validator) {
		this(inventory, validator, () -> false);
	}

	public AutomationItemHandler(ItemStackHandler inventory, BiPredicate<Integer, ItemStack> validator,
			BooleanSupplier extractionLocked) {
		this.inventory = inventory;
		this.validator = validator;
		this.extractionLocked = extractionLocked;
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
		return validator.test(slot, stack) ? inventory.insertItem(slot, stack, simulate) : stack;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return extractionLocked.getAsBoolean() ? ItemStack.EMPTY : inventory.extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		return inventory.getSlotLimit(slot);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return validator.test(slot, stack);
	}
}
