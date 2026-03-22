package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.AbstractWirelessPowerBlockEntity;
import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class WirelessPowerMenu extends AbstractContainerMenu {
	private static final int LINK_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
	private static final int HOTBAR_START = PLAYER_INV_END;
	private static final int HOTBAR_END = HOTBAR_START + 9;
	private final AbstractWirelessPowerBlockEntity blockEntity;
	private final ContainerData data;

	public WirelessPowerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, getBlockEntity(playerInventory, extraData.readBlockPos()), new SimpleContainerData(5));
	}

	public WirelessPowerMenu(int containerId, Inventory playerInventory, AbstractWirelessPowerBlockEntity blockEntity, ContainerData data) {
		super(ModMenuTypes.WIRELESS_POWER.get(), containerId);
		this.blockEntity = blockEntity;
		this.data = data;

		addSlot(new SlotItemHandler(blockEntity.getInventory(), LINK_SLOT, 62, 64));
		addPlayerInventory(playerInventory);
		addPlayerHotbar(playerInventory);
		addDataSlots(data);
	}

	public boolean isTower() {
		return blockEntity instanceof WirelessPowerTowerBlockEntity;
	}

	public int getEnergyStored() {
		return data.get(0);
	}

	public int getMaxEnergyStored() {
		return data.get(1);
	}

	public int getLiveTransferAmount() {
		return data.get(0);
	}

	public boolean hasBoundLinkCard() {
		return data.get(2) != 0;
	}

	public int getLinkedNodeCount() {
		return data.get(3);
	}

	public int getChannel() {
		return data.get(4);
	}

	public ItemStack getLinkCard() {
		return slots.get(LINK_SLOT).getItem();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot sourceSlot = slots.get(index);
		if (!sourceSlot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack sourceStack = sourceSlot.getItem();
		ItemStack copy = sourceStack.copy();

		if (index == LINK_SLOT) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (sourceStack.is(ModItems.LINK_CARD.get())) {
			if (!moveItemStackTo(sourceStack, LINK_SLOT, LINK_SLOT + 1, false)) {
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
		return player.isAlive() && player.level().getBlockEntity(blockEntity.getBlockPos()) == blockEntity;
	}

	private static AbstractWirelessPowerBlockEntity getBlockEntity(Inventory playerInventory, BlockPos blockPos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
		if (blockEntity instanceof AbstractWirelessPowerBlockEntity wireless) {
			return wireless;
		}
		throw new IllegalStateException("Expected wireless power node at " + blockPos);
	}

	private void addPlayerInventory(Inventory playerInventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, 22 + column * 18, 140 + row * 18));
			}
		}
	}

	private void addPlayerHotbar(Inventory playerInventory) {
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 22 + column * 18, 198));
		}
	}
}
