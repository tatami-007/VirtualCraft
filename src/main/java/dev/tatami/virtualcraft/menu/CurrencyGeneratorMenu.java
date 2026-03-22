package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.CurrencyGeneratorBlockEntity;
import dev.tatami.virtualcraft.machine.GeneratorFundingMode;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.registry.ModBlocks;
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

public class CurrencyGeneratorMenu extends AbstractContainerMenu {
	private static final int FUEL_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
	private static final int HOTBAR_START = PLAYER_INV_END;
	private static final int HOTBAR_END = HOTBAR_START + 9;
	private static final int SLOT_X = 48;
	private static final int SLOT_Y = 76;
	private static final int PLAYER_INV_X = 24;
	private static final int PLAYER_INV_Y = 140;
	private static final int HOTBAR_X = 24;
	private static final int HOTBAR_Y = 198;

	private final CurrencyGeneratorBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public CurrencyGeneratorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, getBlockEntity(playerInventory, extraData.readBlockPos()), new SimpleContainerData(7));
	}

	public CurrencyGeneratorMenu(int containerId, Inventory playerInventory, CurrencyGeneratorBlockEntity blockEntity, ContainerData data) {
		super(ModMenuTypes.CURRENCY_GENERATOR.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = playerInventory.player.level();
		this.data = data;

		addSlot(new SlotItemHandler(blockEntity.getInventory(), FUEL_SLOT, SLOT_X, SLOT_Y));
		addPlayerInventory(playerInventory);
		addPlayerHotbar(playerInventory);
		addDataSlots(data);
	}

	public CurrencyGeneratorBlockEntity getBlockEntity() {
		return blockEntity;
	}

	public int getEnergyStored() {
		return data.get(0);
	}

	public int getMaxEnergyStored() {
		return data.get(1);
	}

	public int getBurnTimeRemaining() {
		return data.get(2);
	}

	public int getBurnTimeTotal() {
		return data.get(3);
	}

	public GeneratorFundingMode getFundingMode() {
		return GeneratorFundingMode.values()[Math.max(0, Math.min(GeneratorFundingMode.values().length - 1, data.get(4)))];
	}

	public CryptoCurrency getCurrentBurnCurrency() {
		int value = data.get(5);
		return value < 0 ? null : CryptoCurrency.values()[Math.max(0, Math.min(CryptoCurrency.values().length - 1, value))];
	}

	public CryptoCurrency getLastAutoSelectedCurrency() {
		int value = data.get(6);
		return value < 0 ? null : CryptoCurrency.values()[Math.max(0, Math.min(CryptoCurrency.values().length - 1, value))];
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot sourceSlot = slots.get(index);
		if (!sourceSlot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack sourceStack = sourceSlot.getItem();
		ItemStack copy = sourceStack.copy();

		if (index == FUEL_SLOT) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (CryptoCurrency.byItem(sourceStack) != null) {
			if (!moveItemStackTo(sourceStack, FUEL_SLOT, FUEL_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (index < PLAYER_INV_END) {
			if (!moveItemStackTo(sourceStack, HOTBAR_START, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(sourceStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
			return ItemStack.EMPTY;
		}

		if (sourceStack.isEmpty()) {
			sourceSlot.set(ItemStack.EMPTY);
		} else {
			sourceSlot.setChanged();
		}

		sourceSlot.onTake(player, sourceStack);
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.CURRENCY_GENERATOR.get());
	}

	private static CurrencyGeneratorBlockEntity getBlockEntity(Inventory playerInventory, BlockPos blockPos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
		if (blockEntity instanceof CurrencyGeneratorBlockEntity generator) {
			return generator;
		}
		throw new IllegalStateException("Expected currency generator block entity at " + blockPos);
	}

	private void addPlayerInventory(Inventory playerInventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
			}
		}
	}

	private void addPlayerHotbar(Inventory playerInventory) {
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, HOTBAR_X + column * 18, HOTBAR_Y));
		}
	}
}
